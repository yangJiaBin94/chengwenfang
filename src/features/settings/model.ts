/** Text protocols supported by the LangChain model factory. */
export type TextProtocol = "openai-chat" | "openai-responses";
/** Image protocol for OpenAI-compatible image APIs. */
export type ImageProtocol = "openai-images";
/** Embedding protocol for OpenAI-compatible Embeddings API. */
export type EmbeddingProtocol = "openai-embeddings";

/** Editable provider settings shared by the configuration UI and API client. */
export type AiProviderConfig = {
  name: string;
  model: string;
  baseUrl: string;
  apiKey: string;
  apiKeyConfigured?: boolean;
  protocol: TextProtocol | ImageProtocol | EmbeddingProtocol;
  timeoutSeconds?: number;
  maxRetries?: number;
  temperature?: number | null;
  maxOutputTokens?: number | null;
  imageQuality?: "low" | "medium" | "high" | null;
  enabled?: boolean;
};

/** Independent image, vision, and embedding provider configuration. */
export type AiSettings = {
  image: AiProviderConfig;
  vision: AiProviderConfig;
  embedding: AiProviderConfig;
};

/** Provider kind used by settings cards and connection tests. */
export type AiSettingsKind = "image" | "vision" | "embedding";

/** Business scene used to route calls and persist model-call metadata. */
export type AiScene =
  | "course-draft"
  | "wechat-article"
  | "content-review"
  | "content-repair"
  | "course-image"
  | "wechat-cover";

/** Safe browser defaults used before the persisted settings request completes. */
export const defaultAiSettings: AiSettings = {
  image: { name: "图片 AI", model: "", baseUrl: "", apiKey: "", apiKeyConfigured: false, protocol: "openai-images", timeoutSeconds: 180, maxRetries: 2, imageQuality: "medium", enabled: true },
  vision: { name: "多模态 AI", model: "", baseUrl: "", apiKey: "", apiKeyConfigured: false, protocol: "openai-chat", timeoutSeconds: 180, maxRetries: 2, temperature: 0.2, enabled: true },
  embedding: { name: "向量嵌入", model: "", baseUrl: "", apiKey: "", apiKeyConfigured: false, protocol: "openai-embeddings", timeoutSeconds: 60, maxRetries: 2, enabled: true },
};

/** Selectable upstream protocols for vision completion requests. */
export const textProtocolOptions = [
  { value: "openai-chat", label: "OpenAI Chat Completions" },
  { value: "openai-responses", label: "OpenAI Responses API" }
] as const;

/** Image generation uses the dedicated OpenAI image protocol. */
export const imageProtocolOptions = [
  { value: "openai-images", label: "OpenAI Image API" }
] as const;

/** Vision uses chat protocols (model must support optional images). */
export const visionProtocolOptions = textProtocolOptions;

/** Embedding uses OpenAI-compatible embeddings protocol. */
export const embeddingProtocolOptions = [
  { value: "openai-embeddings", label: "OpenAI Embeddings" },
] as const;
