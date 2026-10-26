/** Text protocols supported by the LangChain model factory. */
export type TextProtocol = "openai-chat" | "openai-responses";
/** Image protocol for OpenAI-compatible image APIs. */
export type ImageProtocol = "openai-images";

/** Editable provider settings shared by the configuration UI and API client. */
export type AiProviderConfig = {
  name: string;
  model: string;
  baseUrl: string;
  apiKey: string;
  apiKeyConfigured?: boolean;
  protocol: TextProtocol | ImageProtocol;
  timeoutSeconds?: number;
  maxRetries?: number;
  temperature?: number | null;
  maxOutputTokens?: number | null;
  imageQuality?: "low" | "medium" | "high" | null;
  enabled?: boolean;
};

/** Independent text, image, and vision provider configuration. */
export type AiSettings = {
  text: AiProviderConfig;
  image: AiProviderConfig;
  vision: AiProviderConfig;
};

/** Provider kind used by settings cards and connection tests. */
export type AiSettingsKind = "text" | "image" | "vision";

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
  text: { name: "文本 AI", model: "", baseUrl: "", apiKey: "", apiKeyConfigured: false, protocol: "openai-chat", timeoutSeconds: 120, maxRetries: 2, temperature: 0.2, enabled: true },
  image: { name: "图片 AI", model: "", baseUrl: "", apiKey: "", apiKeyConfigured: false, protocol: "openai-images", timeoutSeconds: 180, maxRetries: 2, imageQuality: "medium", enabled: true },
  vision: { name: "多模态 AI", model: "", baseUrl: "", apiKey: "", apiKeyConfigured: false, protocol: "openai-chat", timeoutSeconds: 180, maxRetries: 2, temperature: 0.2, enabled: true },
};

/** Selectable upstream protocols for text / vision processing requests. */
export const textProtocolOptions = [
  { value: "openai-chat", label: "OpenAI Chat Completions" },
  { value: "openai-responses", label: "OpenAI Responses API" }
] as const;

/** Image generation uses the dedicated OpenAI image protocol. */
export const imageProtocolOptions = [
  { value: "openai-images", label: "OpenAI Image API" }
] as const;

/** Vision uses the same chat protocols as text (model must support images). */
export const visionProtocolOptions = textProtocolOptions;
