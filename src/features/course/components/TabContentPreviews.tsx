// @ts-nocheck
import { Image, Sparkles, Trash2 } from "lucide-react";
import { EditableMarkdown } from "./courseEditorUi";
import { imageNameFromSrc } from "../model";

export function TabContentPreviews(props: any) {
  const {
    tab, course, content, syncedImages,
    onChangeContent, onResetContent, onConfirm, onDeleteImage,
  } = props;
  if (tab === "draft") {
    const body = content.draft || course.draftExcerpt || "";
    return (
      <div className="preview-card">
        <div className="preview-meta">
          <span>课程原稿</span>
          <span>{course.draftWords || "待生成"} 字</span>
        </div>
        <h3>{course.title}</h3>
        {content.loading ? (
          <p>正在加载课程原稿全文...</p>
        ) : body ? (
          <EditableMarkdown
            content={body}
            label="课程原稿 Markdown"
            onChange={(value) => onChangeContent("draft", value)}
            onReset={() => onResetContent("draft")}
            onConfirm={onConfirm}
          />
        ) : (
          <p>
            当前课程还没有课程原稿。点击“生成原稿”，AI
            会自动读取课程路线、前后课程和知识库规范完成写作。
          </p>
        )}
        {course.draftPath && (
          <blockquote>成文坊副本：{course.draftPath}</blockquote>
        )}
      </div>
    );
  }

  if (tab === "article") {
    const body = content.article || course.articleExcerpt || "";
    return (
      <div className="preview-card article-preview">
        <div className="preview-meta">
          <span>公众号版本</span>
          <span>{course.articleWords || "待生成"} 字</span>
        </div>
        <h3>{body ? "已生成公众号版本" : "公众号版本待生成"}</h3>
        {content.loading ? (
          <p>正在加载公众号文章全文...</p>
        ) : body ? (
          <EditableMarkdown
            content={body}
            hideImageCaptions
            hideImageHeadings
            label="公众号文章 Markdown"
            onChange={(value) => onChangeContent("article", value)}
            onReset={() => onResetContent("article")}
            onConfirm={onConfirm}
          />
        ) : (
          <p>
            当前课程还没有公众号版本。生成课程原稿后，AI
            会自动转换为公众号文章。
          </p>
        )}
        {course.wechatPath && (
          <p className="file-path">成文坊副本：{course.wechatPath}</p>
        )}
      </div>
    );
  }

  if (tab === "images") {
    const images = syncedImages;
    const placeholders: SyncedImage[] = [
      { src: "图片路径待生成", alt: "图片名称待生成", source: "配套图片" },
    ];

    return (
      <div className="image-grid">
        {(images.length ? images : placeholders).map((image, index) => (
          <div className="image-tile" key={`${image.src}-${index}`}>
            {images.length ? (
              <img className="image-preview" src={image.src} alt={image.alt} />
            ) : (
              <div className="image-art">
                {index === 0 ? <Sparkles size={28} /> : <Image size={26} />}
              </div>
            )}
            <strong>{imageNameFromSrc(image.src) || image.alt}</strong>
            <small>{image.src}</small>
            {images.length > 0 && image.src.startsWith("/api/images/") && (
              <button
                className="image-delete-button"
                onClick={() => onDeleteImage(image.src)}
                title="删除图片"
                aria-label={`删除 ${image.alt}`}
              >
                <Trash2 size={15} />
                删除
              </button>
            )}
          </div>
        ))}
      </div>
    );
  }
  return null;
}
