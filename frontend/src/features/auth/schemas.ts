import { z } from "zod";
export const passwordSchema = z
  .string()
  .min(9, "Use at least 9 characters.")
  .regex(/[a-z]/, "Include a lowercase letter.")
  .regex(/[A-Z]/, "Include an uppercase letter.")
  .regex(/[^a-zA-Z0-9]/, "Include a special character.");
export const authSchema = z.object({
  name: z.string(),
  email: z.email("Enter a valid email address."),
  password: z.string(),
});
export const registerSchema = authSchema.extend({
  name: z.string().trim().min(1, "Enter your name."),
  password: passwordSchema,
});
export const loginSchema = authSchema.extend({
  password: z.string().min(1, "Enter your password."),
});
export const resetSchema = z
  .object({ password: passwordSchema, confirmPassword: z.string() })
  .refine((v) => v.password === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords must match.",
  });
