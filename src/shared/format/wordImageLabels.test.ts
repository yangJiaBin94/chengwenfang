import { describe, expect, it } from "vitest";
import {
  imageCountLabel,
  imageCountLabels,
  wordCountLabel,
  wordCountLabels,
} from "./wordImageLabels";

describe("wordCountLabels / imageCountLabels (course)", () => {
  it("keeps course option copy", () => {
    expect(wordCountLabels["2000-4000"]).toBe("2000 到 4000 字");
    expect(imageCountLabels["3-5"]).toBe("3 到 5 张");
  });
});

describe("wordCountLabel / imageCountLabel (hot-news)", () => {
  it("maps known options and defaults", () => {
    expect(wordCountLabel("1500-2500")).toBe("1500-2500 字");
    expect(wordCountLabel(null)).toBe("2500-4000 字");
    expect(imageCountLabel("6-8")).toBe("6-8 张");
    expect(imageCountLabel(undefined)).toBe("3-5 张");
  });
});
