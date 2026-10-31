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

export const granularities = ["DAILY", "MONTHLY", "YEARLY"] as const;
export type Granularity = (typeof granularities)[number];

export const billingPeriods = [
  "DAILY",
  "WEEKLY",
  "MONTHLY",
  "YEARLY",
] as const;
export type BillingPeriod = (typeof billingPeriods)[number];

export type AnalyticsFilters = {
  fromDate?: string;
  toDate?: string;
  asOfDate?: string;
  granularity?: Granularity;
  customerId?: string;
  planId?: string;
  billingPeriod?: string;
  subscriptionState?: string;
  invoiceState?: string;
  paymentMethod?: string;
  windowDays?: number;
};

export type RecentInvoiceItem = {
  id: number;
  invoiceNumber: string;
  subscriptionId: number;
  subscriptionNumber: string;
  customerId: number;
  customerName: string;
  planName: string;
  issueDate: string;
  dueDate: string;
  state: InvoiceState;
  subtotal: number | string;
  discountAmount: number | string;
  taxAmount: number | string;
  total: number | string;
  amountPaid: number | string;
  balanceDue: number | string;
  overdue: boolean;
};

export type RecentPaymentItem = {
  id: number;
  paymentReference: string;
  invoiceId: number;
  invoiceNumber: string;
  customerId: number;
  customerName: string;
  method: PaymentMethod;
  amount: number | string;
  paymentDate: string;
  status: PaymentStatus;
  recordedBy: string;
};

export type UpcomingRenewalItem = {
  subscriptionId: number;
  subscriptionNumber: string;
  customerId: number;
  customerName: string;
  planId: number;
  planName: string;
  billingPeriod: BillingPeriod;
  state: SubscriptionState;
  nextBillingDate: string;
  expiryDate: string;
  daysUntilBilling: number;
  renewalAllowed: boolean;
  cycleAmount: number | string;
  mrrContribution: number | string;
};

export type ExpiringSubscriptionItem = {
  subscriptionId: number;
  subscriptionNumber: string;
  customerId: number;
  customerName: string;
  planId: number;
  planName: string;
  billingPeriod: BillingPeriod;
  state: SubscriptionState;
  expiryDate: string;
  daysUntilExpiry: number;
  renewalAllowed: boolean;
  cycleAmount: number | string;
  mrrContribution: number | string;
};

export type AnalyticsOverview = {
  asOfDate: string;
  fromDate?: string;
  toDate?: string;
  totalSubscriptions: number;
  activeSubscriptions: number;
  pausedSubscriptions: number;
  closedSubscriptions: number;
  draftSubscriptions: number;
  quotationSubscriptions: number;
  confirmedSubscriptions: number;
  newSubscriptions: number;
  mrr: number | string;
  arr: number | string;
  pausedMrr: number | string;
  pausedArr: number | string;
  invoicedSubtotal: number | string;
  invoicedDiscount: number | string;
  invoicedTax: number | string;
  invoicedRevenue: number | string;
  collectedPayments: number | string;
  outstandingReceivables: number | string;
  overdueInvoiceCount: number;
  overdueAmount: number | string;
  upcomingRenewalsCount: number;
  expiringSubscriptionsCount: number;
  recentInvoices: RecentInvoiceItem[];
  recentPayments: RecentPaymentItem[];
  upcomingRenewals: UpcomingRenewalItem[];
  expiringSubscriptions: ExpiringSubscriptionItem[];
};

export type RevenueTrendPoint = {
  period: string;
  label: string;
  invoiceCount: number;
  subtotal: number | string;
  discountAmount: number | string;
  taxAmount: number | string;
  invoicedRevenue: number | string;
  collectedPayments: number | string;
  outstandingAmount: number | string;
};

export type RevenueAnalytics = {
  granularity: Granularity;
  fromDate?: string;
  toDate?: string;
  totalInvoicedSubtotal: number | string;
  totalInvoicedDiscount: number | string;
  totalInvoicedTax: number | string;
  totalInvoicedRevenue: number | string;
  totalCollectedPayments: number | string;
  totalOutstandingReceivables: number | string;
  collectionRatePercent: number | string;
  series: RevenueTrendPoint[];
};

export type StatusCountItem = {
  state: SubscriptionState;
  count: number;
  mrr: number | string;
  arr: number | string;
};

export type PlanBreakdownItem = {
  planId: number;
  planName: string;
  billingPeriod: BillingPeriod;
  activeCount: number;
  pausedCount: number;
  closedCount: number;
  totalCount: number;
  mrr: number | string;
  arr: number | string;
};

export type FrequencyBreakdownItem = {
  billingPeriod: BillingPeriod;
  activeCount: number;
  pausedCount: number;
  totalCount: number;
  mrr: number | string;
  arr: number | string;
};

export type SubscriptionAnalytics = {
  totalSubscriptions: number;
  activeSubscriptions: number;
  pausedSubscriptions: number;
  closedSubscriptions: number;
  newSubscriptions: number;
  mrr: number | string;
  arr: number | string;
  pausedMrr: number | string;
  pausedArr: number | string;
  byStatus: StatusCountItem[];
  byPlan: PlanBreakdownItem[];
  byBillingFrequency: FrequencyBreakdownItem[];
};

export type PaymentMethodBreakdownItem = {
  method: PaymentMethod;
  count: number;
  amount: number | string;
  sharePercent: number | string;
};

export type PaymentTrendPoint = {
  period: string;
  label: string;
  paymentCount: number;
  collectedAmount: number | string;
  reversedAmount: number | string;
};

export type PaymentAnalytics = {
  granularity: Granularity;
  fromDate?: string;
  toDate?: string;
  completedPaymentCount: number;
  reversedPaymentCount: number;
  totalCollectedAmount: number | string;
  totalReversedAmount: number | string;
  byMethod: PaymentMethodBreakdownItem[];
  series: PaymentTrendPoint[];
};

export type AgingBucketItem = {
  bucket: string;
  label: string;
  invoiceCount: number;
  amount: number | string;
};

export type OverdueInvoiceItem = {
  invoiceId: number;
  invoiceNumber: string;
  subscriptionId: number;
  subscriptionNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  planName: string;
  issueDate: string;
  dueDate: string;
  daysOverdue: number;
  agingBucket: string;
  total: number | string;
  amountPaid: number | string;
  balanceDue: number | string;
};

export type ReceivablesAnalytics = {
  asOfDate: string;
  openInvoiceCount: number;
  totalOutstandingAmount: number | string;
  overdueInvoiceCount: number;
  totalOverdueAmount: number | string;
  currentNotDueCount: number;
  currentNotDueAmount: number | string;
  agingBuckets: AgingBucketItem[];
  topOverdueInvoices: OverdueInvoiceItem[];
};

export type RenewalsAnalytics = {
  asOfDate: string;
  windowDays: number;
  upcomingRenewalsCount: number;
  expiringSubscriptionsCount: number;
  upcomingRenewals: UpcomingRenewalItem[];
  expiringSubscriptions: ExpiringSubscriptionItem[];
};

export type CustomerPortalOverview = {
  customerId: number;
  customerName: string;
  customerEmail: string;
  companyName?: string;
  activeSubscriptionCount: number;
  pausedSubscriptionCount: number;
  closedSubscriptionCount: number;
  totalSubscriptionCount: number;
  monthlyRecurringSpend: number | string;
  totalBilledAmount: number | string;
  totalPaidAmount: number | string;
  outstandingBalance: number | string;
  openInvoiceCount: number;
  overdueInvoiceCount: number;
  overdueBalance: number | string;
  nextBillingDate?: string;
  subscriptions: Subscription[];
  recentInvoices: RecentInvoiceItem[];
  recentPayments: RecentPaymentItem[];
};
