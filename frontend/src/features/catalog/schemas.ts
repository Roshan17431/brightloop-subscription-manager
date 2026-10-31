import { z } from "zod";
export const moneySchema = z
  .number()
  .finite()
  .min(0, "Must be zero or greater.")
  .max(9999999999.99)
  .refine(
    (v) => Math.abs(v * 100 - Math.round(v * 100)) < 0.0001,
    "Use at most 2 decimal places.",
  );
const adjustmentSchema = z
  .number()
  .finite()
  .min(-9999999999.99)
  .max(9999999999.99)
  .refine(
    (v) => Math.abs(v * 100 - Math.round(v * 100)) < 0.0001,
    "Use at most 2 decimal places.",
  );
export const productSchema = z
  .object({
    name: z.string().trim().min(1, "Enter a product name."),
    type: z.string().trim().min(1, "Enter a product type."),
    salesPrice: moneySchema,
    costPrice: moneySchema,
    notes: z.string(),
    status: z.enum(["ACTIVE", "INACTIVE"]),
    variants: z.array(
      z.object({
        id: z.number().optional(),
        name: z.string().trim().min(1, "Enter a variant name."),
        priceAdjustment: adjustmentSchema,
      }),
    ),
  })
  .superRefine((product, context) => {
    product.variants.forEach((variant, index) => {
      if (product.salesPrice + variant.priceAdjustment < 0)
        context.addIssue({
          code: "custom",
          path: ["variants", index, "priceAdjustment"],
          message:
            "The base price plus this adjustment must be zero or greater.",
        });
    });
  });
export const planSchema = z
  .object({
    name: z.string().trim().min(1, "Enter a plan name."),
    description: z.string(),
    billingPeriod: z.enum(["DAILY", "WEEKLY", "MONTHLY", "YEARLY"]),
    price: moneySchema,
    minimumQuantity: z
      .number()
      .int()
      .min(1, "Minimum quantity must be at least 1."),
    availableFrom: z.string().min(1, "Choose a start date."),
    availableUntil: z.string().min(1, "Choose an end date."),
    automaticClosing: z.boolean(),
    earlyClosingAllowed: z.boolean(),
    pausingAllowed: z.boolean(),
    renewalAllowed: z.boolean(),
    status: z.enum(["ACTIVE", "INACTIVE"]),
    productIds: z
      .array(z.number())
      .min(1, "Select at least one associated product."),
  })
  .refine((v) => v.availableUntil >= v.availableFrom, {
    path: ["availableUntil"],
    message: "End date must be on or after the start date.",
  });
