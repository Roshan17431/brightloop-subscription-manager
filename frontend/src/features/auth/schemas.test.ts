import { describe, expect, it } from "vitest";
import { passwordSchema, registerSchema, resetSchema } from "./schemas";
import { productSchema, planSchema } from "@/features/catalog/schemas";
import { subscriptionSchema } from "@/features/subscriptions/schemas";
describe("business form validation", () => {
  it.each(["shortA!", "alllowercase!", "ALLUPPERCASE!", "NoSpecial123"])(
    "rejects weak password %s",
    (password) =>
      expect(passwordSchema.safeParse(password).success).toBe(false),
  );
  it("accepts a compliant registration and rejects invalid emails", () => {
    expect(
      registerSchema.safeParse({
        name: "Rin",
        email: "rin@example.test",
        password: "StrongPass!",
      }).success,
    ).toBe(true);
    expect(
      registerSchema.safeParse({
        name: "",
        email: "invalid",
        password: "StrongPass!",
      }).success,
    ).toBe(false);
  });
  it("requires matching reset passwords", () =>
    expect(
      resetSchema.safeParse({
        password: "StrongPass!",
        confirmPassword: "OtherPass!",
      }).success,
    ).toBe(false));
  it("rejects negative and overprecise product prices", () => {
    const value = {
      name: "Product",
      type: "Service",
      salesPrice: 10,
      costPrice: 0,
      notes: "",
      status: "ACTIVE",
      variants: [],
    };
    expect(productSchema.safeParse({ ...value, salesPrice: -1 }).success).toBe(
      false,
    );
    expect(
      productSchema.safeParse({ ...value, salesPrice: 1.234 }).success,
    ).toBe(false);
  });
  it("allows signed variant adjustments while protecting the resulting unit price", () => {
    const product = {
      name: "Product",
      type: "Service",
      salesPrice: 100,
      costPrice: 0,
      notes: "",
      status: "ACTIVE",
      variants: [{ name: "Credit", priceAdjustment: -20 }],
    };
    expect(productSchema.safeParse(product).success).toBe(true);
    expect(
      productSchema.safeParse({
        ...product,
        variants: [{ name: "Invalid credit", priceAdjustment: -101 }],
      }).success,
    ).toBe(false);
  });
  it("rejects reversed availability and missing product associations", () => {
    const value = {
      name: "Plan",
      description: "",
      price: 10,
      billingPeriod: "MONTHLY",
      minimumQuantity: 1,
      availableFrom: "2026-10-31",
      availableUntil: "2026-10-30",
      automaticClosing: false,
      earlyClosingAllowed: false,
      pausingAllowed: false,
      renewalAllowed: true,
      status: "ACTIVE",
      productIds: [1],
    };
    expect(planSchema.safeParse(value).success).toBe(false);
    expect(
      planSchema.safeParse({
        ...value,
        availableUntil: "2026-11-30",
        productIds: [],
      }).success,
    ).toBe(false);
  });
  it("rejects invalid draft date range, quantity and tax", () => {
    const value = {
      planId: 1,
      startDate: "2026-10-31",
      expiryDate: "2026-11-30",
      paymentTerms: "Due",
      lines: [{ productId: 1, quantity: 1, taxRate: 18 }],
    };
    expect(subscriptionSchema.safeParse(value).success).toBe(true);
    expect(
      subscriptionSchema.safeParse({ ...value, expiryDate: "2026-10-01" })
        .success,
    ).toBe(false);
    expect(
      subscriptionSchema.safeParse({
        ...value,
        lines: [{ productId: 1, quantity: 0, taxRate: 101 }],
      }).success,
    ).toBe(false);
  });
});
