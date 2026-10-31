import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PaymentList, PaymentDetail } from "./payments";
import { QuotationDetail } from "../quotations/quotations";
import { SubscriptionDetail } from "../subscriptions/subscriptions";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/payments",
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

describe("Phase 3 Payments, Lifecycle & Quotations UI", () => {
  it("renders payment list with outstanding & overdue summary cards", async () => {
    request.mockImplementation((path: string) => {
      if (path.startsWith("/invoices")) {
        return Promise.resolve({
          content: [
            {
              id: 101,
              invoiceNumber: "INV-20260301-101",
              subscriptionId: 10,
              subscriptionNumber: "BL-SUB-10",
              customerId: 5,
              customerName: "Kochi Ventures",
              customerEmail: "pay@kochi.test",
              planId: 2,
              planName: "Growth Monthly",
              billingPeriod: "MONTHLY",
              billingPeriodStart: "2026-03-01",
              billingPeriodEnd: "2026-03-31",
              issueDate: "2026-03-01",
              dueDate: "2026-03-10",
              paymentTerms: "Net 10",
              state: "CONFIRMED",
              subtotal: "10000.00",
              discountAmount: "0.00",
              taxAmount: "0.00",
              total: "10000.00",
              amountPaid: "4000.00",
              outstandingAmount: "6000.00",
              paymentStatus: "PARTIALLY_PAID",
              partiallyPaid: true,
              overdue: true,
              daysOverdue: 12,
              idempotencyKey: "SUB-10-2026-03-01",
              lines: [],
            },
          ],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 50,
        });
      }
      return Promise.resolve({
        content: [
          {
            id: 501,
            paymentReference: "UTR-4000-ABCD",
            idempotencyKey: "IDEM-501",
            invoiceId: 101,
            invoiceNumber: "INV-20260301-101",
            customerId: 5,
            customerName: "Kochi Ventures",
            method: "UPI",
            amount: "4000.00",
            paymentDate: "2026-03-03",
            status: "COMPLETED",
            recordedBy: "staff@brightloop.local",
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 10,
      });
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <PaymentList
          user={{
            id: 2,
            name: "Staff User",
            email: "staff@brightloop.local",
            role: "STAFF",
          }}
        />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("UTR-4000-ABCD")).toBeInTheDocument();
    expect(screen.getAllByText("6,000.00").length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: /Record payment/i })).toBeInTheDocument();
  });

  it("renders payment detail and allows reversal for staff", async () => {
    request.mockResolvedValue({
      id: 501,
      paymentReference: "UTR-4000-ABCD",
      idempotencyKey: "IDEM-501",
      invoiceId: 101,
      invoiceNumber: "INV-20260301-101",
      customerId: 5,
      customerName: "Kochi Ventures",
      method: "BANK_TRANSFER",
      amount: "4000.00",
      paymentDate: "2026-03-03",
      status: "COMPLETED",
      recordedBy: "staff@brightloop.local",
      invoiceTotal: "10000.00",
      invoiceAmountPaid: "4000.00",
      invoiceOutstandingAmount: "6000.00",
      invoiceState: "CONFIRMED",
      invoicePaymentStatus: "PARTIALLY_PAID",
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <PaymentDetail
          id="501"
          user={{
            id: 2,
            name: "Staff User",
            email: "staff@brightloop.local",
            role: "STAFF",
          }}
        />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByRole("heading", { name: "UTR-4000-ABCD" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Reverse payment/i }),
    ).toBeInTheDocument();
  });

  it("renders subscription lifecycle controls (Pause, Renew, Early close) for active subscription", async () => {
    request.mockImplementation((path: string) => {
      if (path.startsWith("/invoices")) {
        return Promise.resolve({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        });
      }
      return Promise.resolve({
        id: 77,
        number: "BL-SUB-77",
        customerId: 5,
        customerName: "Kochi Ventures",
        planId: 2,
        planName: "Enterprise Flex",
        billingPeriod: "MONTHLY",
        pausingAllowed: true,
        renewalAllowed: true,
        earlyClosingAllowed: true,
        automaticClosing: true,
        startDate: "2026-03-01",
        expiryDate: "2026-12-31",
        nextBillingDate: "2026-04-01",
        paymentTerms: "Net 15",
        state: "ACTIVE",
        total: "5000.00",
        lines: [],
      });
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <SubscriptionDetail
          id="77"
          user={{
            id: 1,
            name: "Admin User",
            email: "admin@brightloop.local",
            role: "ADMIN",
          }}
        />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByRole("heading", { name: "BL-SUB-77" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Pause$/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Renew$/i })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Early close/i }),
    ).toBeInTheDocument();
  });

  it("renders quotation details and convert-to-subscription action", async () => {
    request.mockResolvedValue({
      id: 301,
      quotationNumber: "QTN-20260301-XYZ",
      templateId: 12,
      templateName: "Annual Enterprise Package",
      customerId: 5,
      customerName: "Kochi Ventures",
      planId: 2,
      planName: "Enterprise Plan",
      billingPeriod: "MONTHLY",
      issueDate: "2026-03-01",
      validUntil: "2026-03-31",
      startDate: "2026-04-01",
      expiryDate: "2027-03-31",
      paymentTerms: "Net 15",
      state: "CONFIRMED",
      expired: false,
      subtotal: "4000.00",
      taxAmount: "720.00",
      total: "4720.00",
      createdBy: "staff@brightloop.local",
      lines: [
        {
          id: 1,
          productId: 1,
          productName: "Cloud Suite",
          quantity: 2,
          unitPrice: "2000.00",
          subtotal: "4000.00",
          taxRate: "18.00",
          taxAmount: "720.00",
          amount: "4720.00",
        },
      ],
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <QuotationDetail
          id="301"
          user={{
            id: 2,
            name: "Staff User",
            email: "staff@brightloop.local",
            role: "STAFF",
          }}
        />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByRole("heading", { name: "QTN-20260301-XYZ" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Convert to subscription/i }),
    ).toBeInTheDocument();
  });
});
