import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { discountSchema, taxRuleSchema } from "./schemas";
import { InvoiceDetail } from "./invoices";
import { BillingDashboard } from "./billing";

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: request,
}));

afterEach(() => {
  cleanup();
  request.mockReset();
});

describe("Phase 2 billing, tax, and discount schemas", () => {
  it("validates tax rules and rejects reversed effective dates or rates above 100", () => {
    expect(
      taxRuleSchema.safeParse({
        name: "GST 18%",
        rate: 18,
        isDefault: true,
        effectiveFrom: "2026-01-01",
        effectiveUntil: "2026-12-31",
        status: "ACTIVE",
      }).success,
    ).toBe(true);

    expect(
      taxRuleSchema.safeParse({
        name: "Invalid Rate",
        rate: 105,
        isDefault: false,
        status: "ACTIVE",
      }).success,
    ).toBe(false);

    expect(
      taxRuleSchema.safeParse({
        name: "Reversed Dates",
        rate: 10,
        isDefault: false,
        effectiveFrom: "2026-12-31",
        effectiveUntil: "2026-01-01",
        status: "ACTIVE",
      }).success,
    ).toBe(false);
  });

  it("validates percentage and fixed discounts and product scope requirement", () => {
    expect(
      discountSchema.safeParse({
        name: "Seat Volume 15%",
        type: "PERCENTAGE",
        value: 15,
        scope: "SUBSCRIPTION",
        minimumQuantity: 2,
        usageLimit: 10,
        status: "ACTIVE",
      }).success,
    ).toBe(true);

    expect(
      discountSchema.safeParse({
        name: "Over 100%",
        type: "PERCENTAGE",
        value: 120,
        scope: "SUBSCRIPTION",
        status: "ACTIVE",
      }).success,
    ).toBe(false);

    expect(
      discountSchema.safeParse({
        name: "Product Scope Missing ProductId",
        type: "FIXED",
        value: 50,
        scope: "PRODUCT",
        status: "ACTIVE",
      }).success,
    ).toBe(false);
  });
});

describe("Invoice detail and Billing execution components", () => {
  it("renders invoice financial breakdown and hides manager actions for customer role", async () => {
    request.mockResolvedValue({
      id: 42,
      invoiceNumber: "INV-20261031-ABCD1234",
      subscriptionId: 7,
      subscriptionNumber: "BL-SUB-7",
      customerId: 3,
      customerName: "Acme Corp",
      customerEmail: "billing@acme.test",
      planId: 1,
      planName: "Enterprise Monthly",
      billingPeriod: "MONTHLY",
      billingPeriodStart: "2026-10-01",
      billingPeriodEnd: "2026-10-31",
      issueDate: "2026-10-31",
      dueDate: "2026-11-30",
      paymentTerms: "Net 30",
      state: "DRAFT",
      subtotal: "3120.00",
      discountAmount: "312.00",
      discountName: "Volume 10%",
      taxAmount: "505.44",
      total: "3313.44",
      idempotencyKey: "SUB-7-PERIOD-2026-10-01",
      lines: [
        {
          id: 1,
          productId: 1,
          productName: "Brightloop Suite",
          variantName: "Odoo",
          quantity: 2,
          unitPrice: "1560.00",
          subtotal: "3120.00",
          discountAmount: "312.00",
          taxRate: "18.00",
          taxRuleName: "GST 18%",
          taxAmount: "505.44",
          amount: "3313.44",
        },
      ],
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <InvoiceDetail
          id="42"
          user={{
            id: 9,
            name: "Customer User",
            email: "billing@acme.test",
            role: "CUSTOMER",
          }}
        />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByRole("heading", { name: "INV-20261031-ABCD1234" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("3,313.44").length).toBeGreaterThan(0);
    expect(
      screen.queryByRole("button", { name: /Confirm invoice/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Export PDF/i }),
    ).toBeInTheDocument();
  });

  it("renders billing execution controls and history for admin", async () => {
    request.mockImplementation((path: string) => {
      if (path.startsWith("/billing/due")) {
        return Promise.resolve({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 10,
        });
      }
      return Promise.resolve({
        content: [
          {
            id: 11,
            idempotencyKey: "MANUAL-2026-10-31",
            billingDate: "2026-10-31",
            triggerType: "MANUAL",
            triggeredBy: "admin@brightloop.local",
            status: "COMPLETED",
            totalEvaluated: 3,
            invoicesGenerated: 3,
            skippedCount: 0,
            failedCount: 0,
            expiredClosedCount: 0,
            startedAt: "2026-10-31T04:00:00Z",
            items: [],
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
        <BillingDashboard
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
      await screen.findByRole("button", { name: /Run recurring billing/i }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Run #11")).toBeInTheDocument();
  });
});
