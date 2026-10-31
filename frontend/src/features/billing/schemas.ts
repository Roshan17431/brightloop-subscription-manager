import { z } from "zod";

const moneyPattern = /^\d+(\.\d{1,2})?$/;
const decimal = (max: number, label: string, min = 0) =>
  z.coerce
    .number({ message: `Enter a valid ${label}.` })
    .min(min, `${label[0].toUpperCase() + label.slice(1)} must be at least ${min}.`)
    .max(max, `${label[0].toUpperCase() + label.slice(1)} is too large.`)
    .refine((v) => moneyPattern.test(String(v)), "Use at most 2 decimal places.");

export const taxRuleSchema = z
  .object({
    name: z.string().trim().min(1, "Enter a tax rule name.").max(160),
    description: z.string().max(1000).optional(),
    rate: decimal(100, "tax rate", 0),
    productType: z.string().max(80).optional(),
    productId: z.number().int().positive().optional(),
    isDefault: z.boolean(),
    effectiveFrom: z.string().optional(),
    effectiveUntil: z.string().optional(),
    status: z.enum(["ACTIVE", "INACTIVE"]),
  })
  .refine(
    (v) =>
      !v.effectiveFrom ||
      !v.effectiveUntil ||
      v.effectiveUntil >= v.effectiveFrom,
    {
      path: ["effectiveUntil"],
      message: "End date must be on or after the start date.",
    },
  );

export const discountSchema = z
  .object({
    name: z.string().trim().min(1, "Enter a discount name.").max(160),
    code: z.string().max(64).optional(),
    description: z.string().max(1000).optional(),
    type: z.enum(["PERCENTAGE", "FIXED"]),
    value: decimal(9999999999, "discount value", 0.01),
    scope: z.enum(["SUBSCRIPTION", "PRODUCT"]),
    productId: z.number().int().positive().optional(),
    subscriptionId: z.number().int().positive().optional(),
    minimumQuantity: z
      .union([z.coerce.number().int().min(1).max(1000000), z.literal("")])
      .optional(),
    minimumPurchase: z
      .union([decimal(9999999999, "minimum purchase", 0), z.literal("")])
      .optional(),
    validFrom: z.string().optional(),
    validUntil: z.string().optional(),
    usageLimit: z
      .union([z.coerce.number().int().min(1).max(10000000), z.literal("")])
      .optional(),
    status: z.enum(["ACTIVE", "INACTIVE"]),
  })
  .refine((v) => v.type !== "PERCENTAGE" || v.value <= 100, {
    path: ["value"],
    message: "Percentage discount cannot exceed 100%.",
  })
  .refine((v) => v.scope !== "PRODUCT" || Boolean(v.productId), {
    path: ["productId"],
    message: "Choose a product for a product-scoped discount.",
  })
  .refine(
    (v) => !v.validFrom || !v.validUntil || v.validUntil >= v.validFrom,
    {
      path: ["validUntil"],
      message: "End date must be on or after the start date.",
    },
  );
