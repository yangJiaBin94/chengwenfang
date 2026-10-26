import { describe, expect, it } from "vitest";
import { formatVideoDuration } from "../../../shared/format/duration";

describe("formatVideoDuration", () => {
  it("formats under one hour as m:ss", () => {
    expect(formatVideoDuration(0)).toBe("0:00");
    expect(formatVideoDuration(65)).toBe("1:05");
    expect(formatVideoDuration(599)).toBe("9:59");
  });

  it("formats one hour or more as h:mm:ss", () => {
    expect(formatVideoDuration(3600)).toBe("1:00:00");
    expect(formatVideoDuration(3661)).toBe("1:01:01");
  });

  it("returns null for missing or invalid values", () => {
    expect(formatVideoDuration(null)).toBeNull();
    expect(formatVideoDuration(undefined)).toBeNull();
    expect(formatVideoDuration(Number.NaN)).toBeNull();
    expect(formatVideoDuration(-1)).toBeNull();
  });
});
