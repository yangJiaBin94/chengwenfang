import { describe, expect, it } from "vitest";
import { resolveHotNewsDocumentId } from "./model";

describe("hotnews model", () => {
  it("resolveHotNewsDocumentId 仅接受显式 id，不再派生", () => {
    expect(resolveHotNewsDocumentId("art-1", "  already-id  ")).toBe("already-id");
    expect(resolveHotNewsDocumentId("art-1", null)).toBe("");
    expect(resolveHotNewsDocumentId("art-1", "")).toBe("");
  });
});
