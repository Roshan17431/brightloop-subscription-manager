import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Dashboard } from "./dashboard";
import { FinancialReports } from "../analytics/reports";
import { money } from "@/lib/utils";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/dashboard",
  useSearchParams: () => new URLSearchParams(),
}));

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: request,
}));

afterEach(() => {
  cleanup();
  request.mockReset();
});

const mockOverview = {
  asOfDate: "2026-06-15",
  fromDate: undefined,
  toDate: undefined,
  totalSubscriptions: 12,
  activeSubscriptions: 8,
  pausedSubscriptions: 2,
  closedSubscriptions: 1,
  draftSubscriptions: 1,
  quotationSubscriptions: 0,
  confirmedSubscriptions: 0,
  newSubscriptions: 3,
  mrr: "48500.00",
  arr: "582000.00",
  pausedMrr: "6000.00",
  pausedArr: "72000.00",
  invoicedSubtotal: "120000.00",
  invoicedDiscount: "5000.00",
  invoicedTax: "20700.00",
  invoicedRevenue: "135700.00",
  collectedPayments: "110000.00",
  outstandingReceivables: "25700.00",
  overdueInvoiceCount: 2,
  overdueAmount: "15700.00",
  upcomingRenewalsCount: 4,
  expiringSubscriptionsCount: 1,
  recentInvoices: [
    {
      id: 201,
      invoiceNumber: "INV-20260601-201",
      subscriptionId: 11,
      subscriptionNumber: "BL-SUB-011",
      customerId: 7,
      customerName: "Malabar Cloud Systems",
      planName: "Enterprise Monthly",
      issueDate: "2026-06-01",
      dueDate: "2026-06-10",
      state: "CONFIRMED",
      subtotal: "10000.00",
      discountAmount: "0.00",
      taxAmount: "1800.00",
      total: "11800.00",
      amountPaid: "0.00",
      balanceDue: "11800.00",
      overdue: true,
    },
  ],
  recentPayments: [
    {
      id: 301,
      paymentReference: "UTR-998877",
      invoiceId: 200,
      invoiceNumber: "INV-20260501-200",
      customerId: 7,
      customerName: "Malabar Cloud Systems",
      method: "BANK_TRANSFER",
      amount: "11800.00",
      paymentDate: "2026-05-12",
      status: "COMPLETED",
      recordedBy: "admin@brightloop.local",
    },
  ],
  upcomingRenewals: [
    {
      subscriptionId: 11,
      subscriptionNumber: "BL-SUB-011",
      customerId: 7,
      customerName: "Malabar Cloud Systems",
      planId: 3,
      planName: "Enterprise Monthly",
      billingPeriod: "MONTHLY",
      state: "ACTIVE",
      nextBillingDate: "2026-07-01",
      expiryDate: "2026-12-31",
      daysUntilBilling: 16,
      renewalAllowed: true,
      cycleAmount: "10000.00",
      mrrContribution: "10000.00",
    },
  ],
  expiringSubscriptions: [
    {
      subscriptionId: 12,
      subscriptionNumber: "BL-SUB-012",
      customerId: 8,
      customerName: "Infopark Labs",
      planId: 4,
      planName: "Starter Monthly",
      billingPeriod: "MONTHLY",
      state: "ACTIVE",
      expiryDate: "2026-06-28",
      daysUntilExpiry: 13,
      renewalAllowed: true,
      cycleAmount: "2500.00",
      mrrContribution: "2500.00",
    },
  ],
};

const mockRevenue = {
  granularity: "MONTHLY",
  totalInvoicedSubtotal: "120000.00",
  totalInvoicedDiscount: "5000.00",
  totalInvoicedTax: "20700.00",
  totalInvoicedRevenue: "135700.00",
  totalCollectedPayments: "110000.00",
  totalOutstandingReceivables: "25700.00",
  collectionRatePercent: "81.06",
  series: [
    {
      period: "2026-05",
      label: "May 2026",
      invoiceCount: 5,
      subtotal: "60000.00",
      discountAmount: "2500.00",
      taxAmount: "10350.00",
      invoicedRevenue: "67850.00",
      collectedPayments: "67850.00",
      outstandingAmount: "0.00",
    },
    {
      period: "2026-06",
      label: "Jun 2026",
      invoiceCount: 6,
      subtotal: "60000.00",
      discountAmount: "2500.00",
      taxAmount: "10350.00",
      invoicedRevenue: "67850.00",
      collectedPayments: "42150.00",
      outstandingAmount: "25700.00",
    },
  ],
};

const mockSubAnalytics = {
  totalSubscriptions: 12,
  activeSubscriptions: 8,
  pausedSubscriptions: 2,
  closedSubscriptions: 1,
  newSubscriptions: 3,
  mrr: "48500.00",
  arr: "582000.00",
  pausedMrr: "6000.00",
  pausedArr: "72000.00",
  byStatus: [
    { state: "ACTIVE", count: 8, mrr: "48500.00", arr: "582000.00" },
    { state: "PAUSED", count: 2, mrr: "6000.00", arr: "72000.00" },
    { state: "CLOSED", count: 1, mrr: "0.00", arr: "0.00" },
  ],
  byPlan: [
    {
      planId: 3,
      planName: "Enterprise Monthly",
      billingPeriod: "MONTHLY",
      activeCount: 4,
      pausedCount: 1,
      closedCount: 0,
      totalCount: 5,
      mrr: "40000.00",
      arr: "480000.00",
    },
  ],
  byBillingFrequency: [
    {
      billingPeriod: "MONTHLY",
      activeCount: 8,
      pausedCount: 2,
      totalCount: 10,
      mrr: "48500.00",
      arr: "582000.00",
    },
  ],
};

const mockPayAnalytics = {
  granularity: "MONTHLY",
  completedPaymentCount: 9,
  reversedPaymentCount: 1,
  totalCollectedAmount: "110000.00",
  totalReversedAmount: "2500.00",
  byMethod: [
    {
      method: "BANK_TRANSFER",
      count: 6,
      amount: "85000.00",
      sharePercent: "77.27",
    },
    { method: "UPI", count: 3, amount: "25000.00", sharePercent: "22.73" },
  ],
  series: [
    {
      period: "2026-06",
      label: "Jun 2026",
      paymentCount: 4,
      collectedAmount: "42150.00",
      reversedAmount: "0.00",
    },
  ],
};

const mockReceivables = {
  asOfDate: "2026-06-15",
  openInvoiceCount: 3,
  totalOutstandingAmount: "25700.00",
  overdueInvoiceCount: 2,
  totalOverdueAmount: "15700.00",
  currentNotDueCount: 1,
  currentNotDueAmount: "10000.00",
  agingBuckets: [
    {
      bucket: "1-30",
      label: "1–30 days overdue",
      invoiceCount: 1,
      amount: "11800.00",
    },
    {
      bucket: "31-60",
      label: "31–60 days overdue",
      invoiceCount: 1,
      amount: "3900.00",
    },
    {
      bucket: "61-90",
      label: "61–90 days overdue",
      invoiceCount: 0,
      amount: "0.00",
    },
    {
      bucket: "90+",
      label: "90+ days overdue",
      invoiceCount: 0,
      amount: "0.00",
    },
  ],
  topOverdueInvoices: [
    {
      invoiceId: 201,
      invoiceNumber: "INV-20260601-201",
      subscriptionId: 11,
      subscriptionNumber: "BL-SUB-011",
      customerId: 7,
      customerName: "Malabar Cloud Systems",
      customerEmail: "finance@malabar.test",
      planName: "Enterprise Monthly",
      issueDate: "2026-06-01",
      dueDate: "2026-06-10",
      daysOverdue: 5,
      agingBucket: "1-30",
      total: "11800.00",
      amountPaid: "0.00",
      balanceDue: "11800.00",
    },
  ],
};

function setupAnalyticsMocks() {
  request.mockImplementation((path: string) => {
    if (path.startsWith("/analytics/overview"))
      return Promise.resolve(mockOverview);
    if (path.startsWith("/analytics/revenue"))
      return Promise.resolve(mockRevenue);
    if (path.startsWith("/analytics/subscriptions"))
      return Promise.resolve(mockSubAnalytics);
    if (path.startsWith("/analytics/payments"))
      return Promise.resolve(mockPayAnalytics);
    if (path.startsWith("/analytics/receivables"))
      return Promise.resolve(mockReceivables);
    if (path.startsWith("/plans"))
      return Promise.resolve({
        content: [{ id: 3, name: "Enterprise Monthly" }],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 100,
      });
    if (path.startsWith("/customers"))
      return Promise.resolve({
        content: [{ id: 7, name: "Malabar Cloud Systems" }],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 100,
      });
    return Promise.resolve({});
  });
}

describe("Phase 4 Executive Dashboard, Customer Portal & Financial Reports", () => {
  it("renders admin executive dashboard KPI cards, tables, and filters", async () => {
    setupAnalyticsMocks();

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <Dashboard
          user={{
            id: 1,
            name: "Admin Executive",
            email: "admin@brightloop.local",
            role: "ADMIN",
          }}
        />
      </QueryClientProvider>,
    );

    expect(await screen.findByText(money("48500.00"))).toBeInTheDocument();
    expect(screen.getByText(money("582000.00"))).toBeInTheDocument();
    expect(screen.getByText(money("135700.00"))).toBeInTheDocument();
    expect(screen.getByText(money("110000.00"))).toBeInTheDocument();
    expect(screen.getByText(money("25700.00"))).toBeInTheDocument();
    expect(screen.getByText(money("15700.00"))).toBeInTheDocument();

    expect(screen.getAllByText("INV-20260601-201").length).toBeGreaterThan(0);
    expect(screen.getAllByText("UTR-998877").length).toBeGreaterThan(0);
    expect(screen.getAllByText("BL-SUB-011").length).toBeGreaterThan(0);
    expect(screen.getAllByText("BL-SUB-012").length).toBeGreaterThan(0);

    // Change granularity filter and reset
    const granSelect = screen.getByLabelText("Trend granularity");
    fireEvent.change(granSelect, { target: { value: "YEARLY" } });
    expect((granSelect as HTMLSelectElement).value).toBe("YEARLY");

    fireEvent.click(screen.getByRole("button", { name: /Reset filters/i }));
    expect((granSelect as HTMLSelectElement).value).toBe("MONTHLY");
  });

  it("renders customer self-service portal dashboard with lifecycle buttons and PDF download", async () => {
    request.mockImplementation((path: string) => {
      if (path === "/portal/overview") {
        return Promise.resolve({
          customerId: 7,
          customerName: "Malabar Cloud Systems",
          customerEmail: "finance@malabar.test",
          companyName: "Malabar Cloud",
          activeSubscriptionCount: 1,
          pausedSubscriptionCount: 0,
          closedSubscriptionCount: 0,
          totalSubscriptionCount: 1,
          monthlyRecurringSpend: "5000.00",
          totalBilledAmount: "5900.00",
          totalPaidAmount: "2000.00",
          outstandingBalance: "3900.00",
          openInvoiceCount: 1,
          overdueInvoiceCount: 1,
          overdueBalance: "3900.00",
          nextBillingDate: "2026-07-01",
          subscriptions: [
            {
              id: 55,
              number: "BL-PORTAL-055",
              customerId: 7,
              customerName: "Malabar Cloud Systems",
              planId: 3,
              planName: "Flex Monthly",
              billingPeriod: "MONTHLY",
              pausingAllowed: true,
              renewalAllowed: true,
              earlyClosingAllowed: true,
              startDate: "2026-06-01",
              expiryDate: "2026-12-31",
              nextBillingDate: "2026-07-01",
              paymentTerms: "Net 15",
              state: "ACTIVE",
              total: "5900.00",
              lines: [],
            },
          ],
          recentInvoices: [
            {
              id: 701,
              invoiceNumber: "INV-PORTAL-701",
              subscriptionId: 55,
              subscriptionNumber: "BL-PORTAL-055",
              customerId: 7,
              customerName: "Malabar Cloud Systems",
              planName: "Flex Monthly",
              issueDate: "2026-06-01",
              dueDate: "2026-06-10",
              state: "CONFIRMED",
              subtotal: "5000.00",
              discountAmount: "0.00",
              taxAmount: "900.00",
              total: "5900.00",
              amountPaid: "2000.00",
              balanceDue: "3900.00",
              overdue: true,
            },
          ],
          recentPayments: [
            {
              id: 801,
              paymentReference: "UPI-PORTAL-801",
              invoiceId: 701,
              invoiceNumber: "INV-PORTAL-701",
              customerId: 7,
              customerName: "Malabar Cloud Systems",
              method: "UPI",
              amount: "2000.00",
              paymentDate: "2026-06-05",
              status: "COMPLETED",
              recordedBy: "staff@brightloop.local",
            },
          ],
        });
      }
      return Promise.resolve({});
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <Dashboard
          user={{
            id: 7,
            name: "Anjali Nair",
            email: "finance@malabar.test",
            role: "CUSTOMER",
          }}
        />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("BL-PORTAL-055")).toBeInTheDocument();
    expect(screen.getAllByText("INV-PORTAL-701").length).toBeGreaterThan(0);
    expect(screen.getByText("UPI-PORTAL-801")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Pause/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Renew/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /PDF/i })).toHaveAttribute(
      "href",
      expect.stringContaining("/invoices/701/pdf"),
    );
  });

  it("renders financial reports screen and switches across all 4 report tabs", async () => {
    setupAnalyticsMocks();

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <FinancialReports
          user={{
            id: 1,
            name: "Admin Executive",
            email: "admin@brightloop.local",
            role: "ADMIN",
          }}
        />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("May 2026")).toBeInTheDocument();
    expect(screen.getByText("81.06%")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Payment report/i }));
    expect(await screen.findByText("77.27%")).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: /Subscription & MRR summary/i }),
    );
    expect(await screen.findByText("Enterprise Monthly")).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: /Outstanding & overdue aging/i }),
    );
    expect(await screen.findByText("INV-20260601-201")).toBeInTheDocument();
    expect(screen.getByText("1–30 days overdue")).toBeInTheDocument();
  });
});
