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
export const subscriptionFilterStates = [
  "DRAFT",
  "QUOTATION",
  "CONFIRMED",
  "ACTIVE",
  "PAUSED",
  "CLOSED",
] as const;
export type SubscriptionState = (typeof subscriptionFilterStates)[number];
export type Subscription = {
  id: number;
  number: string;
  customerId: number;
  customerName: string;
  planId: number;
  planName: string;
  billingPeriod?: "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";
  pausingAllowed?: boolean;
  renewalAllowed?: boolean;
  earlyClosingAllowed?: boolean;
  automaticClosing?: boolean;
  startDate: string;
  expiryDate: string;
  nextBillingDate?: string;
  lastBilledDate?: string;
  paymentTerms: string;
  state: SubscriptionState;
  total: number | string;
  pausedAt?: string;
  pausedBy?: string;
  pausedDate?: string;
  resumedAt?: string;
  resumedBy?: string;
  totalPausedDays?: number;
  closedAt?: string;
  closedBy?: string;
  closureReason?: string;
  renewedAt?: string;
  renewedBy?: string;
  renewalCount?: number;
  quotationTemplateId?: number;
  quotationId?: number;
  quotationValidUntil?: string;
  convertedFromQuotationAt?: string;
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

export const invoiceStates = [
  "DRAFT",
  "CONFIRMED",
  "PAID",
  "CANCELLED",
] as const;
export type InvoiceState = (typeof invoiceStates)[number];

export type InvoiceLine = {
  id: number;
  productId: number;
  productName: string;
  variantId?: number;
  variantName?: string;
  quantity: number;
  unitPrice: number | string;
  subtotal: number | string;
  discountAmount: number | string;
  discountId?: number;
  discountName?: string;
  taxRate: number | string;
  taxRuleId?: number;
  taxRuleName?: string;
  taxAmount: number | string;
  amount: number | string;
};

export type Invoice = {
  id: number;
  invoiceNumber: string;
  subscriptionId: number;
  subscriptionNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  customerCompany?: string;
  billingAddress?: string;
  planId: number;
  planName: string;
  billingPeriod: "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";
  billingPeriodStart: string;
  billingPeriodEnd: string;
  issueDate: string;
  dueDate: string;
  paymentTerms: string;
  state: InvoiceState;
  subtotal: number | string;
  discountAmount: number | string;
  discountId?: number;
  discountName?: string;
  taxAmount: number | string;
  total: number | string;
  amountPaid?: number | string;
  outstandingAmount?: number | string;
  paymentStatus?: "UNPAID" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
  partiallyPaid?: boolean;
  overdue?: boolean;
  daysOverdue?: number;
  idempotencyKey: string;
  billingRunId?: number;
  paymentReference?: string;
  paidAt?: string;
  confirmedAt?: string;
  cancelledAt?: string;
  sentAt?: string;
  lastFollowupAt?: string;
  followupNotes?: string;
  followupBy?: string;
  notes?: string;
  lines: InvoiceLine[];
  createdAt?: string;
  updatedAt?: string;
};

export const paymentMethods = [
  "BANK_TRANSFER",
  "CASH",
  "UPI",
  "CARD",
  "OTHER",
] as const;
export type PaymentMethod = (typeof paymentMethods)[number];

export const paymentStatuses = ["COMPLETED", "REVERSED"] as const;
export type PaymentStatus = (typeof paymentStatuses)[number];

export type Payment = {
  id: number;
  paymentReference: string;
  idempotencyKey: string;
  invoiceId: number;
  invoiceNumber: string;
  customerId: number;
  customerName: string;
  method: PaymentMethod;
  amount: number | string;
  paymentDate: string;
  status: PaymentStatus;
  note?: string;
  recordedBy: string;
  reversedAt?: string;
  reversedBy?: string;
  reversalReason?: string;
  reversalReference?: string;
  invoiceTotal?: number | string;
  invoiceAmountPaid?: number | string;
  invoiceOutstandingAmount?: number | string;
  invoiceState?: InvoiceState;
  invoicePaymentStatus?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type QuotationTemplateLine = {
  id: number;
  productId: number;
  productName: string;
  variantId?: number;
  variantName?: string;
  quantity: number;
  unitPrice: number | string;
  taxRate: number | string;
  amount: number | string;
};

export type QuotationTemplate = {
  id: number;
  name: string;
  description?: string;
  planId: number;
  planName: string;
  billingPeriod?: string;
  validityDays: number;
  paymentTerms: string;
  status: "ACTIVE" | "INACTIVE";
  estimatedTotal: number | string;
  lines: QuotationTemplateLine[];
  createdAt?: string;
  updatedAt?: string;
};

export const quotationStates = [
  "DRAFT",
  "SENT",
  "CONFIRMED",
  "CONVERTED",
  "EXPIRED",
  "CANCELLED",
] as const;
export type QuotationState = (typeof quotationStates)[number];

export type QuotationLine = {
  id: number;
  productId: number;
  productName: string;
  variantId?: number;
  variantName?: string;
  quantity: number;
  unitPrice: number | string;
  subtotal: number | string;
  taxRate: number | string;
  taxAmount: number | string;
  amount: number | string;
};

export type Quotation = {
  id: number;
  quotationNumber: string;
  templateId?: number;
  templateName?: string;
  customerId: number;
  customerName: string;
  planId: number;
  planName: string;
  billingPeriod?: string;
  issueDate: string;
  validUntil: string;
  startDate: string;
  expiryDate: string;
  paymentTerms: string;
  state: QuotationState;
  expired: boolean;
  subtotal: number | string;
  taxAmount: number | string;
  total: number | string;
  notes?: string;
  convertedSubscriptionId?: number;
  convertedSubscriptionNumber?: string;
  confirmedAt?: string;
  convertedAt?: string;
  cancelledAt?: string;
  createdBy: string;
  lines: QuotationLine[];
  createdAt?: string;
  updatedAt?: string;
};

export type TaxRule = {
  id: number;
  name: string;
  description?: string;
  rate: number | string;
  productType?: string;
  productId?: number;
  productName?: string;
  isDefault: boolean;
  effectiveFrom?: string;
  effectiveUntil?: string;
  status: "ACTIVE" | "INACTIVE";
  createdAt?: string;
  updatedAt?: string;
};

export type Discount = {
  id: number;
  name: string;
  code?: string;
  description?: string;
  type: "PERCENTAGE" | "FIXED";
  value: number | string;
  scope: "SUBSCRIPTION" | "PRODUCT";
  productId?: number;
  productName?: string;
  subscriptionId?: number;
  subscriptionNumber?: string;
  minimumQuantity?: number;
  minimumPurchase?: number | string;
  validFrom?: string;
  validUntil?: string;
  usageLimit?: number;
  usedCount: number;
  status: "ACTIVE" | "INACTIVE";
  createdAt?: string;
  updatedAt?: string;
};

export type BillingRunItem = {
  id: number;
  subscriptionId: number;
  subscriptionNumber: string;
  billingPeriodStart: string;
  status: "INVOICED" | "SKIPPED" | "EXPIRED_CLOSED" | "FAILED";
  invoiceId?: number;
  invoiceNumber?: string;
  attemptCount: number;
  message?: string;
  createdAt?: string;
};

export type BillingRun = {
  id: number;
  idempotencyKey: string;
  billingDate: string;
  triggerType: "MANUAL" | "SCHEDULED" | "RETRY";
  triggeredBy: string;
  status: "RUNNING" | "COMPLETED" | "COMPLETED_WITH_ERRORS" | "FAILED";
  totalEvaluated: number;
  invoicesGenerated: number;
  skippedCount: number;
  failedCount: number;
  expiredClosedCount: number;
  startedAt: string;
  completedAt?: string;
  errorSummary?: string;
  items: BillingRunItem[];
};
