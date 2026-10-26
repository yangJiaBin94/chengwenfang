# 热点资料池视频解析 CLI

工作台在资料池中手动「解析视频」时，由 Java Job **按步骤**调用本目录模块：

1. 默认：字幕 → 音频 → Whisper →（Java）文本总结  
2. 口播无效时：下载视频 → ffmpeg 抽关键帧 →（Java）多模态总结，并标记 `visual`

**不常驻服务。** 未安装依赖时，调研仍可用，解析会失败并提示原因。

## 入口

```powershell
# 激活 venv 后
python -m video_transcript --url <视频URL> --work-dir <目录> --step fetch_subtitle --lang zh
```

步骤：`probe_duration` | `fetch_subtitle` | `download_audio` | `convert_format` | `asr` | `download_video` | `extract_frames`  
stdout 输出一行 JSON：`ok` / `step` / `text` / `source` / `error` / `artifacts`；`probe_duration` 另含 `durationSeconds`。

时长上限由 Java 配置 `workbench.video-transcript.max-duration-minutes` 控制（默认 30；`<=0` 不限制），在下载/ASR 前跳过超长视频。

抽帧相关参数（可由 Java 透传）：

- `--vision-max-frames`（默认 12）
- `--scene-threshold`（默认 0.3）
- `--fps-floor-seconds`（默认 8）
- `--dedup-diff-percent`（默认 8）
- `--dedup-window`（默认 4）
- `--frame-max-width`（默认 1024）

## 依赖安装（Windows）

| 依赖 | 作用 |
|------|------|
| Python 3.11+ | 跑本 CLI |
| ffmpeg | 抽音频 / 转 mp3 / 切镜抽帧（须在 PATH） |
| yt-dlp | 下载字幕、音频或视频（pip） |
| openai-whisper | 本地语音识别（pip） |
| Pillow / numpy | 关键帧滑动去重（pip） |

```powershell
winget install Python.Python.3.12
winget install Gyan.FFmpeg

cd scripts\video-transcript
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -U pip
pip install -r requirements.txt

ffmpeg -version
yt-dlp --version
python -c "import whisper, PIL, numpy; print('ok')"
```

首次 Whisper 会下载 `base` 模型（约一百多 MB）。可选代理：`HTTP_PROXY` / `HTTPS_PROXY`。

## 工作目录产物（供重试）

`.workbench/video-transcript/{itemId}/` 下可能出现：`subtitle.vtt` / `raw.txt` / `audio.mp3` / `video.mp4` / `frames/` / `frames_manifest.json` 等。

默认保留 **3 天**（`workbench.video-transcript.work-dir-retain-days`）：应用**启动时清理一次**，之后**每小时**再扫一次，删除目录内最新文件时间已超期的条目目录。重试时若本地产物已不在，会自动回退步骤并重新下载。

## 抽帧规则摘要

1. ffmpeg `scene` 切镜检测  
2. 密度下限：至少每 N 秒考虑一帧  
3. 滑动窗口像素差去重（防 A-B-A）  
4. 硬上限（默认 12），优先保留首尾  
