import { describe, expect, it } from "vitest";
import { adjustment, money, moneySum } from "./utils";

describe("exact decimal financial presentation", () => {
  it("preserves cents for a large positive BigDecimal string", () => {
    expect(money("9999989999990000.01")).toBe("9,99,99,89,99,99,90,000.01");
  });
  it("preserves cents and sign for a large negative BigDecimal string", () => {
    expect(money("-9999989999990000.01")).toBe("-9,99,99,89,99,99,90,000.01");
  });
  it.each([
    ["0", "0.00"],
    [0, "0.00"],
    ["0.01", "0.01"],
    ["1234567.5", "12,34,567.50"],
    [1234.5, "1,234.50"],
    ["-42.10", "-42.10"],
    ["+00042.1", "42.10"],
    ["1.005", "1.01"],
  ])("formats ordinary amount %s", (value, expected) => {
    expect(money(value)).toBe(expected);
  });
  it("formats signed adjustments without converting decimal strings to Number", () => {
    expect(adjustment("9999989999990000.01")).toBe(
      "+9,99,99,89,99,99,90,000.01",
    );
    expect(adjustment("-9999989999990000.01")).toBe(
      "-9,99,99,89,99,99,90,000.01",
    );
    expect(adjustment("0.00")).toBe("+0.00");
  });
  it("adds product price and variant adjustment using exact cents", () => {
    expect(moneySum("9999989999990000.01", "0.02")).toBe(
      "9,99,99,89,99,99,90,000.03",
    );
    expect(moneySum("100.10", "-0.20")).toBe("99.90");
  });
});
