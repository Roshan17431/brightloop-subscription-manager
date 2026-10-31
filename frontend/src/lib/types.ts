export type Role = "ADMIN" | "STAFF" | "CUSTOMER";
export type User = { id: number; name: string; email: string; role: Role };
export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};
export type Customer = {
  id: number;
  userId?: number;
  name: string;
  email: string;
  phone?: string;
  companyName?: string;
  billingAddress?: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
};
export type Variant = {
  id?: number;
  name: string;
  priceAdjustment: number | string;
};
export type Product = {
  id: number;
  name: string;
  type: string;
  salesPrice: number | string;
  costPrice: number | string | null;
  notes?: string;
  status: string;
  variants: Variant[];
  createdAt?: string;
  updatedAt?: string;
};
export type Plan = {
  id: number;
  name: string;
  description?: string;
  price: number | string;
  billingPeriod: "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";
  minimumQuantity: number;
  availableFrom: string;
  availableUntil: string;
  automaticClosing: boolean;
  earlyClosingAllowed: boolean;
  pausingAllowed: boolean;
  renewalAllowed: boolean;
  status: string;
  productIds: number[];
  createdAt?: string;
  updatedAt?: string;
};
export const states = [
  "DRAFT",
  "QUOTATION",
  "CONFIRMED",
  "ACTIVE",
  "CLOSED",
] as const;
export type SubscriptionState = (typeof states)[number];
export type Subscription = {
  id: number;
  number: string;
  customerId: number;
  customerName: string;
  planId: number;
  planName: string;
  startDate: string;
  expiryDate: string;
  paymentTerms: string;
  state: SubscriptionState;
  total: number | string;
  lines: {
    id: number;
    productId: number;
    productName: string;
    variantId?: number;
    variantName?: string;
    quantity: number;
    unitPrice: number | string;
    taxRate: number | string;
    amount: number | string;
  }[];
};
