import { z } from "zod";
export const subscriptionSchema = z
  .object({
    customerId: z.number().int().positive("Choose a customer.").optional(),
    planId: z.number().int().positive("Choose a plan."),
    startDate: z.string().min(1, "Choose a start date."),
    expiryDate: z.string().min(1, "Choose an expiry date."),
    paymentTerms: z.string().trim().min(1, "Enter payment terms."),
    lines: z
      .array(
        z.object({
          productId: z.number().int().positive("Choose a product."),
          variantId: z.number().optional(),
          quantity: z.number().int().min(1, "Quantity must be at least 1."),
          taxRate: z
            .number()
            .min(0)
            .max(100)
            .refine(
              (v) => Math.abs(v * 100 - Math.round(v * 100)) < 0.0001,
              "Use at most 2 decimal places.",
            ),
        }),
      )
      .min(1, "Add at least one subscription line."),
  })
  .refine((v) => v.expiryDate >= v.startDate, {
    path: ["expiryDate"],
    message: "Expiry must be on or after the start date.",
  });
