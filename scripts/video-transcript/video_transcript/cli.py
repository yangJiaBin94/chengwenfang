"""
逐步视频解析 CLI：字幕优先，否则下载音频 → 转格式 → Whisper；
画面兜底：下载视频 → 抽关键帧。
stdout 输出一行 JSON，供 Java ProcessBuilder 解析。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path


STEPS = (
    "probe_duration",
    "fetch_subtitle",
    "download_audio",
    "convert_format",
    "asr",
    "download_video",
    "extract_frames",
)


def configure_stdio_utf8() -> None:
    """避免 Windows 默认 GBK 导致含特殊字符的结果无法写出。"""
    os.environ.setdefault("PYTHONIOENCODING", "utf-8")
    os.environ.setdefault("PYTHONUTF8", "1")
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
        except Exception:  # noqa: BLE001 — 部分包装流不支持 reconfigure
            pass


def emit(payload: dict) -> int:
    """打印一行 JSON 并返回进程码；ASCII 转义保证管道编码安全。"""
    line = json.dumps(payload, ensure_ascii=True) + "\n"
    try:
        sys.stdout.write(line)
        sys.stdout.flush()
    except UnicodeEncodeError:
        sys.stdout.buffer.write(line.encode("utf-8", errors="replace"))
        sys.stdout.buffer.flush()
    return 0 if payload.get("ok") else 1


def yt_dlp_cmd(*args: str) -> list[str]:
    """用当前解释器调用 yt-dlp，避免 PATH 上的旧版系统 yt-dlp 导致 403。"""
    return [sys.executable, "-m", "yt_dlp", *args]


def run_cmd(args: list[str], cwd: Path | None = None, timeout: int = 900) -> subprocess.CompletedProcess:
    """执行外部命令。"""
    return subprocess.run(
        args,
        cwd=str(cwd) if cwd else None,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
        check=False,
    )


def strip_subtitle_text(content: str) -> str:
    """去掉 VTT/SRT 时间轴，保留纯文本。"""
    lines: list[str] = []
    for raw in content.splitlines():
        line = raw.strip()
        if not line or line.upper().startswith("WEBVTT"):
            continue
        if re.match(r"^\d+$", line):
            continue
        if re.search(r"\d{2}:\d{2}:\d{2}", line) and "-->" in line:
            continue
        if line.startswith("NOTE"):
            continue
        lines.append(line)
    # 合并相邻重复行
    out: list[str] = []
    for line in lines:
        if out and out[-1] == line:
            continue
        out.append(line)
    return "\n".join(out).strip()


def step_probe_duration(url: str, work_dir: Path) -> dict:
    """仅拉取元数据获取时长（秒），不下载音视频。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    proc = run_cmd(
        yt_dlp_cmd(
            "--skip-download",
            "--no-playlist",
            "--no-update",
            "--print",
            "%(duration)s",
            url,
        ),
        cwd=work_dir,
        timeout=120,
    )
    duration: float | None = None
    for line in (proc.stdout or "").splitlines():
        text = line.strip()
        if not text or text.lower() in {"na", "none", "null", "nan"}:
            continue
        try:
            value = float(text)
        except ValueError:
            continue
        if value > 0:
            duration = value
            break
    if duration is None:
        return {
            "ok": False,
            "step": "probe_duration",
            "text": "",
            "source": None,
            "error": (proc.stderr or proc.stdout or "无法获取视频时长")[:800],
            "artifacts": {},
            "durationSeconds": None,
        }
    return {
        "ok": True,
        "step": "probe_duration",
        "text": "",
        "source": "metadata",
        "error": None,
        "artifacts": {},
        "durationSeconds": duration,
    }


def step_fetch_subtitle(url: str, work_dir: Path, lang: str) -> dict:
    """尝试下载字幕；成功则写入 raw.txt。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    existing = work_dir / "raw.txt"
    if existing.is_file() and existing.stat().st_size > 20:
        text = existing.read_text(encoding="utf-8", errors="replace").strip()
        if text:
            return {
                "ok": True,
                "step": "fetch_subtitle",
                "text": text,
                "source": "subtitle",
                "error": None,
                "artifacts": {"raw": str(existing)},
            }

    outtmpl = str(work_dir / "sub")
    # 不下载视频，只写字幕
    proc = run_cmd(
        yt_dlp_cmd(
            "--skip-download",
            "--write-sub",
            "--write-auto-sub",
            "--sub-langs",
            f"{lang}.*,zh.*,zh-Hans,zh-CN,en.*,en",
            "--sub-format",
            "vtt/srt/best",
            "--no-update",
            "-o",
            outtmpl,
            url,
        ),
        cwd=work_dir,
        timeout=180,
    )
    subs = list(work_dir.glob("sub*.vtt")) + list(work_dir.glob("sub*.srt"))
    if not subs:
        return {
            "ok": False,
            "step": "fetch_subtitle",
            "text": "",
            "source": None,
            "error": (proc.stderr or proc.stdout or "未找到字幕文件")[:800],
            "artifacts": {},
        }
    text = strip_subtitle_text(subs[0].read_text(encoding="utf-8", errors="replace"))
    if len(text) < 20:
        return {
            "ok": False,
            "step": "fetch_subtitle",
            "text": "",
            "source": None,
            "error": "字幕文本过短或为空",
            "artifacts": {"subtitle": str(subs[0])},
        }
    raw_path = work_dir / "raw.txt"
    raw_path.write_text(text, encoding="utf-8")
    return {
        "ok": True,
        "step": "fetch_subtitle",
        "text": text,
        "source": "subtitle",
        "error": None,
        "artifacts": {"subtitle": str(subs[0]), "raw": str(raw_path)},
    }


def step_download_audio(url: str, work_dir: Path) -> dict:
    """下载 bestaudio（可能已是 mp3）。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    mp3 = work_dir / "audio.mp3"
    if mp3.is_file() and mp3.stat().st_size > 1024:
        return {
            "ok": True,
            "step": "download_audio",
            "text": "",
            "source": None,
            "error": None,
            "artifacts": {"audio": str(mp3)},
        }

    outtmpl = str(work_dir / "audio.%(ext)s")
    proc = run_cmd(
        yt_dlp_cmd(
            "-f",
            "bestaudio/best",
            "--extract-audio",
            "--audio-format",
            "mp3",
            "--audio-quality",
            "128",
            "--no-update",
            "-o",
            outtmpl,
            url,
        ),
        cwd=work_dir,
        timeout=900,
    )
    # yt-dlp 可能写成 audio.mp3
    candidates = list(work_dir.glob("audio.*"))
    audio_path = None
    for path in candidates:
        if path.suffix.lower() in {".mp3", ".m4a", ".webm", ".opus", ".ogg", ".wav"}:
            audio_path = path
            break
    if audio_path is None:
        return {
            "ok": False,
            "step": "download_audio",
            "text": "",
            "source": None,
            "error": (proc.stderr or proc.stdout or "音频下载失败")[:800],
            "artifacts": {},
        }
    if audio_path.suffix.lower() != ".mp3":
        # 留给 convert_format
        return {
            "ok": True,
            "step": "download_audio",
            "text": "",
            "source": None,
            "error": None,
            "artifacts": {"audio": str(audio_path)},
        }
    return {
        "ok": True,
        "step": "download_audio",
        "text": "",
        "source": None,
        "error": None,
        "artifacts": {"audio": str(audio_path)},
    }


def step_convert_format(work_dir: Path) -> dict:
    """确保存在 audio.mp3。"""
    mp3 = work_dir / "audio.mp3"
    if mp3.is_file() and mp3.stat().st_size > 1024:
        return {
            "ok": True,
            "step": "convert_format",
            "text": "",
            "source": None,
            "error": None,
            "artifacts": {"audio": str(mp3)},
        }
    sources = [
        p
        for p in work_dir.glob("audio.*")
        if p.suffix.lower() in {".m4a", ".webm", ".opus", ".ogg", ".wav", ".mp4"}
    ]
    if not sources:
        return {
            "ok": False,
            "step": "convert_format",
            "text": "",
            "source": None,
            "error": "找不到可转换的音频文件",
            "artifacts": {},
        }
    src = sources[0]
    proc = run_cmd(
        ["ffmpeg", "-y", "-i", str(src), "-vn", "-acodec", "libmp3lame", "-q:a", "4", str(mp3)],
        cwd=work_dir,
        timeout=300,
    )
    if not mp3.is_file() or mp3.stat().st_size < 1024:
        return {
            "ok": False,
            "step": "convert_format",
            "text": "",
            "source": None,
            "error": (proc.stderr or proc.stdout or "ffmpeg 转换失败")[:800],
            "artifacts": {"audio_src": str(src)},
        }
    return {
        "ok": True,
        "step": "convert_format",
        "text": "",
        "source": None,
        "error": None,
        "artifacts": {"audio": str(mp3)},
    }


def step_asr(work_dir: Path, lang: str) -> dict:
    """Whisper 转写 audio.mp3。"""
    raw_path = work_dir / "raw.txt"
    if raw_path.is_file() and raw_path.stat().st_size > 20:
        text = raw_path.read_text(encoding="utf-8", errors="replace").strip()
        if text:
            return {
                "ok": True,
                "step": "asr",
                "text": text,
                "source": "asr",
                "error": None,
                "artifacts": {"raw": str(raw_path)},
            }

    mp3 = work_dir / "audio.mp3"
    if not mp3.is_file():
        return {
            "ok": False,
            "step": "asr",
            "text": "",
            "source": None,
            "error": "缺少 audio.mp3，请先完成下载/转格式",
            "artifacts": {},
        }
    try:
        import whisper
    except ImportError as exc:
        return {
            "ok": False,
            "step": "asr",
            "text": "",
            "source": None,
            "error": f"未安装 openai-whisper: {exc}",
            "artifacts": {},
        }

    model_size = os.environ.get("WHISPER_MODEL", "base")
    model = whisper.load_model(model_size)
    # verbose=False 减少进度条特殊字符打到管道
    result = model.transcribe(
        str(mp3),
        language=lang if lang else None,
        verbose=False,
    )
    text = (result.get("text") or "").strip()
    if len(text) < 10:
        return {
            "ok": False,
            "step": "asr",
            "text": "",
            "source": None,
            "error": "语音识别结果过短或为空",
            "artifacts": {"audio": str(mp3)},
        }
    raw_path.write_text(text, encoding="utf-8")
    return {
        "ok": True,
        "step": "asr",
        "text": text,
        "source": "asr",
        "error": None,
        "artifacts": {"raw": str(raw_path), "audio": str(mp3)},
    }


def step_download_video(url: str, work_dir: Path) -> dict:
    """下载视频文件（建议高度 ≤720），复用已有 video.mp4。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    video = work_dir / "video.mp4"
    if video.is_file() and video.stat().st_size > 10_000:
        return {
            "ok": True,
            "step": "download_video",
            "text": "",
            "source": None,
            "error": None,
            "artifacts": {"video": str(video)},
        }

    outtmpl = str(work_dir / "video.%(ext)s")
    proc = run_cmd(
        yt_dlp_cmd(
            "-f",
            "bv*[height<=720]+ba/b[height<=720]/best",
            "--merge-output-format",
            "mp4",
            "--no-update",
            "-o",
            outtmpl,
            url,
        ),
        cwd=work_dir,
        timeout=900,
    )
    candidates = sorted(work_dir.glob("video.*"), key=lambda p: p.stat().st_mtime, reverse=True)
    chosen: Path | None = None
    for path in candidates:
        if path.suffix.lower() in {".mp4", ".webm", ".mkv", ".mov"} and path.stat().st_size > 10_000:
            chosen = path
            break
    if chosen is None:
        return {
            "ok": False,
            "step": "download_video",
            "text": "",
            "source": None,
            "error": (proc.stderr or proc.stdout or "视频下载失败")[:800],
            "artifacts": {},
        }
    if chosen.resolve() != video.resolve():
        if video.exists():
            video.unlink(missing_ok=True)
        chosen.replace(video)
    return {
        "ok": True,
        "step": "download_video",
        "text": "",
        "source": None,
        "error": None,
        "artifacts": {"video": str(video)},
    }


def probe_duration_seconds(video: Path) -> float:
    """用 ffprobe 读取时长秒数。"""
    proc = run_cmd(
        [
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            str(video),
        ],
        timeout=60,
    )
    try:
        return max(0.1, float((proc.stdout or "").strip()))
    except ValueError:
        return 60.0


def detect_scene_timestamps(video: Path, threshold: float) -> list[float]:
    """ffmpeg scene 检测，解析 showinfo 中的 pts_time。"""
    proc = run_cmd(
        [
            "ffmpeg",
            "-i",
            str(video),
            "-filter:v",
            f"select='gt(scene,{threshold})',showinfo",
            "-f",
            "null",
            "-",
        ],
        timeout=600,
    )
    text = (proc.stderr or "") + "\n" + (proc.stdout or "")
    times: list[float] = []
    for match in re.finditer(r"pts_time:([0-9.]+)", text):
        try:
            times.append(float(match.group(1)))
        except ValueError:
            continue
    return times


def merge_timestamps(
    scene_times: list[float],
    duration: float,
    fps_floor_seconds: int,
    max_frames: int,
) -> list[float]:
    """合并切镜、首尾与密度下限时间点，并做最小间隔去重。"""
    candidates: list[float] = [0.0]
    if duration > 1.0:
        candidates.append(max(0.0, duration - 0.5))
    candidates.extend(scene_times)
    floor = max(1, fps_floor_seconds)
    t = float(floor)
    while t < duration:
        candidates.append(t)
        t += float(floor)
    candidates = sorted(set(round(x, 3) for x in candidates if 0 <= x <= duration + 0.05))
    # 最小间隔 0.5s
    merged: list[float] = []
    for ts in candidates:
        if not merged or (ts - merged[-1]) >= 0.5:
            merged.append(ts)
    if len(merged) <= max_frames:
        return merged
    # 锁首尾后均匀下采样
    if max_frames <= 2:
        return [merged[0], merged[-1]][:max_frames]
    inner = merged[1:-1]
    keep_inner = max_frames - 2
    if keep_inner <= 0 or not inner:
        return [merged[0], merged[-1]]
    if len(inner) <= keep_inner:
        return [merged[0], *inner, merged[-1]]
    step = (len(inner) - 1) / (keep_inner - 1) if keep_inner > 1 else 0
    picked = [inner[int(round(i * step))] for i in range(keep_inner)]
    return [merged[0], *picked, merged[-1]]


def extract_frame_at(video: Path, timestamp: float, out_path: Path, max_width: int) -> bool:
    """在指定时间戳截取一帧 JPEG。"""
    out_path.parent.mkdir(parents=True, exist_ok=True)
    vf = f"scale='min({max_width},iw)':-2"
    proc = run_cmd(
        [
            "ffmpeg",
            "-y",
            "-ss",
            f"{timestamp:.3f}",
            "-i",
            str(video),
            "-frames:v",
            "1",
            "-q:v",
            "3",
            "-vf",
            vf,
            str(out_path),
        ],
        timeout=120,
    )
    return out_path.is_file() and out_path.stat().st_size > 100


def frame_diff_percent(a_path: Path, b_path: Path) -> float:
    """缩小后比较像素差异百分比；失败时视为差异很大。"""
    try:
        from PIL import Image
        import numpy as np
    except ImportError:
        return 100.0
    try:
        with Image.open(a_path) as img_a, Image.open(b_path) as img_b:
            size = (64, 64)
            arr_a = np.asarray(img_a.convert("RGB").resize(size), dtype="int16")
            arr_b = np.asarray(img_b.convert("RGB").resize(size), dtype="int16")
        diff = np.abs(arr_a - arr_b).mean() / 255.0 * 100.0
        return float(diff)
    except Exception:  # noqa: BLE001
        return 100.0


def sliding_dedup(
    frame_items: list[tuple[float, Path]],
    dedup_diff_percent: int,
    dedup_window: int,
) -> list[tuple[float, Path]]:
    """与最近若干保留帧比较，去掉近重复。"""
    kept: list[tuple[float, Path]] = []
    window = max(1, dedup_window)
    threshold = float(max(1, dedup_diff_percent))
    for ts, path in frame_items:
        duplicate = False
        for _, prev in kept[-window:]:
            if frame_diff_percent(path, prev) < threshold:
                duplicate = True
                break
        if duplicate:
            try:
                path.unlink(missing_ok=True)
            except Exception:  # noqa: BLE001
                pass
            continue
        kept.append((ts, path))
    return kept


def step_extract_frames(
    work_dir: Path,
    vision_max_frames: int,
    scene_threshold: float,
    fps_floor_seconds: int,
    dedup_diff_percent: int,
    dedup_window: int,
    frame_max_width: int,
) -> dict:
    """切镜 + 密度下限 + 滑动去重 + 硬上限，产出 frames/*.jpg。"""
    video = work_dir / "video.mp4"
    if not video.is_file():
        return {
            "ok": False,
            "step": "extract_frames",
            "text": "",
            "source": None,
            "error": "缺少 video.mp4，请先完成下载视频",
            "artifacts": {},
        }

    frames_dir = work_dir / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    # 清理旧帧
    for old in frames_dir.glob("frame_*.jpg"):
        old.unlink(missing_ok=True)

    duration = probe_duration_seconds(video)
    max_frames = max(1, vision_max_frames)
    if duration < 3.0:
        timestamps = [0.0]
        if duration > 0.5:
            timestamps.append(duration / 2)
        if duration > 1.0:
            timestamps.append(max(0.0, duration - 0.2))
        timestamps = timestamps[: min(3, max_frames)]
    else:
        scene_times = detect_scene_timestamps(video, scene_threshold)
        timestamps = merge_timestamps(scene_times, duration, fps_floor_seconds, max_frames * 2)

    extracted: list[tuple[float, Path]] = []
    for index, ts in enumerate(timestamps, start=1):
        out = frames_dir / f"frame_{index:04d}.jpg"
        if extract_frame_at(video, ts, out, frame_max_width):
            extracted.append((ts, out))

    extracted = sliding_dedup(extracted, dedup_diff_percent, dedup_window)
    if len(extracted) > max_frames:
        # 再次均匀下采样并重命名
        if max_frames <= 2:
            extracted = [extracted[0], extracted[-1]][:max_frames]
        else:
            inner = extracted[1:-1]
            keep_inner = max_frames - 2
            step = (len(inner) - 1) / (keep_inner - 1) if keep_inner > 1 and inner else 0
            picked = [inner[int(round(i * step))] for i in range(keep_inner)] if inner else []
            extracted = [extracted[0], *picked, extracted[-1]]

    # 规范文件名顺序
    final_items: list[dict] = []
    for index, (ts, path) in enumerate(extracted, start=1):
        target = frames_dir / f"frame_{index:04d}.jpg"
        if path.resolve() != target.resolve():
            if target.exists():
                target.unlink(missing_ok=True)
            path.replace(target)
        final_items.append({"path": str(target), "timestamp_sec": round(ts, 3)})

    # 清理多余旧文件
    keep_names = {item["path"] for item in final_items}
    for old in frames_dir.glob("frame_*.jpg"):
        if str(old) not in keep_names:
            old.unlink(missing_ok=True)

    manifest = work_dir / "frames_manifest.json"
    manifest.write_text(json.dumps(final_items, ensure_ascii=False, indent=2), encoding="utf-8")

    if not final_items:
        return {
            "ok": False,
            "step": "extract_frames",
            "text": "",
            "source": None,
            "error": "未抽到有效关键帧",
            "artifacts": {"video": str(video)},
        }

    return {
        "ok": True,
        "step": "extract_frames",
        "text": "",
        "source": "frames",
        "error": None,
        "artifacts": {"video": str(video), "frames": final_items, "manifest": str(manifest)},
    }


def main(argv: list[str] | None = None) -> int:
    configure_stdio_utf8()
    parser = argparse.ArgumentParser(description="Workbench video transcript step runner")
    parser.add_argument("--url", required=True, help="视频页面 URL")
    parser.add_argument("--work-dir", required=True, help="产物目录")
    parser.add_argument("--step", required=True, choices=STEPS, help="执行步骤")
    parser.add_argument("--lang", default="zh", help="语言代码")
    parser.add_argument("--vision-max-frames", type=int, default=12)
    parser.add_argument("--scene-threshold", type=float, default=0.3)
    parser.add_argument("--fps-floor-seconds", type=int, default=8)
    parser.add_argument("--dedup-diff-percent", type=int, default=8)
    parser.add_argument("--dedup-window", type=int, default=4)
    parser.add_argument("--frame-max-width", type=int, default=1024)
    args = parser.parse_args(argv)

    work_dir = Path(args.work_dir).resolve()
    try:
        if args.step == "probe_duration":
            return emit(step_probe_duration(args.url, work_dir))
        if args.step == "fetch_subtitle":
            return emit(step_fetch_subtitle(args.url, work_dir, args.lang))
        if args.step == "download_audio":
            return emit(step_download_audio(args.url, work_dir))
        if args.step == "convert_format":
            return emit(step_convert_format(work_dir))
        if args.step == "asr":
            return emit(step_asr(work_dir, args.lang))
        if args.step == "download_video":
            return emit(step_download_video(args.url, work_dir))
        if args.step == "extract_frames":
            return emit(
                step_extract_frames(
                    work_dir,
                    args.vision_max_frames,
                    args.scene_threshold,
                    args.fps_floor_seconds,
                    args.dedup_diff_percent,
                    args.dedup_window,
                    args.frame_max_width,
                )
            )
        return emit(
            {
                "ok": False,
                "step": args.step,
                "text": "",
                "source": None,
                "error": f"未知步骤: {args.step}",
                "artifacts": {},
            }
        )
    except subprocess.TimeoutExpired:
        return emit(
            {
                "ok": False,
                "step": args.step,
                "text": "",
                "source": None,
                "error": "步骤执行超时",
                "artifacts": {},
            }
        )
    except Exception as exc:  # noqa: BLE001 — CLI 边界统一 JSON
        return emit(
            {
                "ok": False,
                "step": args.step,
                "text": "",
                "source": None,
                "error": str(exc)[:800],
                "artifacts": {},
            }
        )


if __name__ == "__main__":
    raise SystemExit(main())
