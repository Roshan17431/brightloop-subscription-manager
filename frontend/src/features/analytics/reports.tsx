"use client";
import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowUpRight,
  Download,
  Filter,
  RotateCcw,
  Search,
} from "lucide-react";
import { api, queryString } from "@/lib/api";
import { human, money } from "@/lib/utils";
import {
  billingPeriods,
  granularities,
  invoiceStates,
  paymentMethods,
  type AnalyticsFilters,
  type Customer,
  type Granularity,
  type Page,
  type PaymentAnalytics,
  type Plan,
  type ReceivablesAnalytics,
  type RevenueAnalytics,
  type SubscriptionAnalytics,
  type User,
} from "@/lib/types";
import {
  Badge,
  Empty,
  ErrorState,
  Heading,
  Loading,
} from "@/components/shared";
import { Button } from "@/components/ui/button";

type ReportTab = "revenue" | "payments" | "subscriptions" | "receivables";

export function FinancialReports({ user }: { user: User }) {
  const [tab, setTab] = useState<ReportTab>("revenue");
  const [search, setSearch] = useState("");
  const [filters, setFilters] = useState<AnalyticsFilters>({
    granularity: "MONTHLY",
    windowDays: 30,
  });

  const qs = queryString({
    fromDate: filters.fromDate || undefined,
    toDate: filters.toDate || undefined,
    granularity: filters.granularity || "MONTHLY",
    customerId: filters.customerId || undefined,
    planId: filters.planId || undefined,
    billingPeriod: filters.billingPeriod || undefined,
    invoiceState: filters.invoiceState || undefined,
    paymentMethod: filters.paymentMethod || undefined,
    windowDays: filters.windowDays || 30,
  });

  const exportQs = queryString({
    section: tab,
    fromDate: filters.fromDate || undefined,
    toDate: filters.toDate || undefined,
    granularity: filters.granularity || "MONTHLY",
    customerId: filters.customerId || undefined,
    planId: filters.planId || undefined,
    billingPeriod: filters.billingPeriod || undefined,
    invoiceState: filters.invoiceState || undefined,
    paymentMethod: filters.paymentMethod || undefined,
  });

  const revenue = useQuery({
    queryKey: ["analytics", "revenue", qs],
    queryFn: ({ signal }) =>
      api<RevenueAnalytics>(`/analytics/revenue${qs}`, { signal }),
    enabled: tab === "revenue",
  });

  const payments = useQuery({
    queryKey: ["analytics", "payments", qs],
    queryFn: ({ signal }) =>
      api<PaymentAnalytics>(`/analytics/payments${qs}`, { signal }),
    enabled: tab === "payments",
  });

  const subscriptions = useQuery({
    queryKey: ["analytics", "subscriptions", qs],
    queryFn: ({ signal }) =>
      api<SubscriptionAnalytics>(`/analytics/subscriptions${qs}`, { signal }),
    enabled: tab === "subscriptions",
  });

  const receivables = useQuery({
    queryKey: ["analytics", "receivables", qs],
    queryFn: ({ signal }) =>
      api<ReceivablesAnalytics>(`/analytics/receivables${qs}`, { signal }),
    enabled: tab === "receivables",
  });

  const plans = useQuery({
    queryKey: ["plans", "reports-filter"],
    queryFn: ({ signal }) => api<Page<Plan>>("/plans?size=100", { signal }),
  });

  const customers = useQuery({
    queryKey: ["customers", "reports-filter"],
    queryFn: ({ signal }) =>
      api<Page<Customer>>("/customers?size=100", { signal }),
  });

  function resetFilters() {
    setSearch("");
    setFilters({
      fromDate: "",
      toDate: "",
      granularity: "MONTHLY",
      customerId: "",
      planId: "",
      billingPeriod: "",
      invoiceState: "",
      paymentMethod: "",
      windowDays: 30,
    });
  }

  const q = search.trim().toLowerCase();

  return (
    <>
      <Heading
        eyebrow="Financial & operational reporting"
        title="Financial reports"
        description={`Exportable PostgreSQL financial summaries for ${user.name}.`}
        action={
          <Button asChild>
            <a
              href={`/api/v1/analytics/reports/export${exportQs}`}
              download={`brightloop-${tab}-report.csv`}
            >
              <Download size={15} />
              Export {tab} CSV
            </a>
          </Button>
        }
      />

      {/* Tabs */}
      <div className="mb-6 flex flex-wrap gap-2 border-b border-slate-200 pb-3">
        {(
          [
            ["revenue", "Revenue report"],
            ["payments", "Payment report"],
            ["subscriptions", "Subscription & MRR summary"],
            ["receivables", "Outstanding & overdue aging"],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={`rounded-lg px-4 py-2 text-xs font-semibold transition ${
              tab === key
                ? "bg-violet-600 text-white shadow-sm"
                : "bg-white text-slate-600 hover:bg-slate-100"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Filter Bar */}
      <section className="card mb-6 p-4 sm:p-5">
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-700">
            <Filter size={14} className="text-violet-600" />
            Report filters & search
          </div>
          <button
            type="button"
            onClick={resetFilters}
            className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-violet-600"
          >
            <RotateCcw size={12} />
            Reset all
          </button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-8">
          <div className="xl:col-span-2">
            <label
              htmlFor="rep-search"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Search rows
            </label>
            <div className="relative">
              <Search
                size={14}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              />
              <input
                id="rep-search"
                placeholder="Filter period, plan, customer, invoice…"
                className="input pl-8"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label
              htmlFor="rep-from"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              From date
            </label>
            <input
              id="rep-from"
              className="input"
              type="date"
              value={filters.fromDate || ""}
              onChange={(e) =>
                setFilters((f) => ({ ...f, fromDate: e.target.value }))
              }
            />
          </div>
          <div>
            <label
              htmlFor="rep-to"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              To date
            </label>
            <input
              id="rep-to"
              className="input"
              type="date"
              value={filters.toDate || ""}
              onChange={(e) =>
                setFilters((f) => ({ ...f, toDate: e.target.value }))
              }
            />
          </div>
          <div>
            <label
              htmlFor="rep-granularity"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Granularity
            </label>
            <select
              id="rep-granularity"
              className="input"
              value={filters.granularity || "MONTHLY"}
              onChange={(e) =>
                setFilters((f) => ({
                  ...f,
                  granularity: e.target.value as Granularity,
                }))
              }
            >
              {granularities.map((g) => (
                <option key={g} value={g}>
                  {human(g)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label
              htmlFor="rep-freq"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Billing frequency
            </label>
            <select
              id="rep-freq"
              className="input"
              value={filters.billingPeriod || ""}
              onChange={(e) =>
                setFilters((f) => ({ ...f, billingPeriod: e.target.value }))
              }
            >
              <option value="">All frequencies</option>
              {billingPeriods.map((bp) => (
                <option key={bp} value={bp}>
                  {human(bp)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label
              htmlFor="rep-plan"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Plan
            </label>
            <select
              id="rep-plan"
              className="input"
              value={filters.planId || ""}
              onChange={(e) =>
                setFilters((f) => ({ ...f, planId: e.target.value }))
              }
            >
              <option value="">All plans</option>
              {plans.data?.content.map((p) => (
                <option key={p.id} value={String(p.id)}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label
              htmlFor="rep-customer"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Customer
            </label>
            <select
              id="rep-customer"
              className="input"
              value={filters.customerId || ""}
              onChange={(e) =>
                setFilters((f) => ({ ...f, customerId: e.target.value }))
              }
            >
              <option value="">All customers</option>
              {customers.data?.content.map((c) => (
                <option key={c.id} value={String(c.id)}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        </div>
        {(tab === "revenue" || tab === "payments") && (
          <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {tab === "revenue" && (
              <div>
                <label
                  htmlFor="rep-inv-state"
                  className="mb-1 block text-[11px] font-medium text-slate-500"
                >
                  Invoice status
                </label>
                <select
                  id="rep-inv-state"
                  className="input"
                  value={filters.invoiceState || ""}
                  onChange={(e) =>
                    setFilters((f) => ({ ...f, invoiceState: e.target.value }))
                  }
                >
                  <option value="">Confirmed & Paid (Recognized)</option>
                  {invoiceStates.map((st) => (
                    <option key={st} value={st}>
                      {human(st)}
                    </option>
                  ))}
                </select>
              </div>
            )}
            <div>
              <label
                htmlFor="rep-pay-method"
                className="mb-1 block text-[11px] font-medium text-slate-500"
              >
                Payment method
              </label>
              <select
                id="rep-pay-method"
                className="input"
                value={filters.paymentMethod || ""}
                onChange={(e) =>
                  setFilters((f) => ({ ...f, paymentMethod: e.target.value }))
                }
              >
                <option value="">All methods</option>
                {paymentMethods.map((pm) => (
                  <option key={pm} value={pm}>
                    {human(pm)}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}
      </section>

      {/* Tab 1: Revenue Report */}
      {tab === "revenue" && (
        <>
          {revenue.isPending ? (
            <Loading />
          ) : revenue.error ? (
            <ErrorState error={revenue.error} retry={() => revenue.refetch()} />
          ) : (
            <>
              <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
                <SummaryCard
                  label="Pre-Tax Subtotal"
                  value={money(revenue.data.totalInvoicedSubtotal)}
                />
                <SummaryCard
                  label="Discounts Applied"
                  value={money(revenue.data.totalInvoicedDiscount)}
                />
                <SummaryCard
                  label="Taxes Billed"
                  value={money(revenue.data.totalInvoicedTax)}
                />
                <SummaryCard
                  label="Total Invoiced Revenue"
                  value={money(revenue.data.totalInvoicedRevenue)}
                />
                <SummaryCard
                  label="Collected Payments"
                  value={money(revenue.data.totalCollectedPayments)}
                />
                <SummaryCard
                  label="Collection Rate"
                  value={`${revenue.data.collectionRatePercent}%`}
                />
              </div>

              <section className="card overflow-hidden">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="text-sm font-semibold">
                    Revenue & collection schedule ({human(revenue.data.granularity)})
                  </h2>
                </div>
                {!revenue.data.series.length ? (
                  <Empty
                    title="No revenue rows match filters"
                    description="Adjust your date range, plan, or customer filter to view revenue periods."
                  />
                ) : (
                  <div className="overflow-x-auto">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>Period</th>
                          <th>Invoices</th>
                          <th>Pre-Tax Subtotal</th>
                          <th>Discounts</th>
                          <th>Taxes</th>
                          <th>Invoiced Revenue</th>
                          <th>Collected Payments</th>
                          <th>Outstanding</th>
                        </tr>
                      </thead>
                      <tbody>
                        {revenue.data.series
                          .filter(
                            (row) =>
                              !q ||
                              row.period.toLowerCase().includes(q) ||
                              row.label.toLowerCase().includes(q),
                          )
                          .map((row) => (
                            <tr key={row.period}>
                              <td className="font-semibold">{row.label}</td>
                              <td>{row.invoiceCount}</td>
                              <td>{money(row.subtotal)}</td>
                              <td>{money(row.discountAmount)}</td>
                              <td>{money(row.taxAmount)}</td>
                              <td className="font-semibold text-violet-700">
                                {money(row.invoicedRevenue)}
                              </td>
                              <td className="font-semibold text-teal-700">
                                {money(row.collectedPayments)}
                              </td>
                              <td className="font-medium text-amber-700">
                                {money(row.outstandingAmount)}
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
        </>
      )}

      {/* Tab 2: Payment Report */}
      {tab === "payments" && (
        <>
          {payments.isPending ? (
            <Loading />
          ) : payments.error ? (
            <ErrorState
              error={payments.error}
              retry={() => payments.refetch()}
            />
          ) : (
            <>
              <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <SummaryCard
                  label="Completed Payments"
                  value={String(payments.data.completedPaymentCount)}
                />
                <SummaryCard
                  label="Total Collected Amount"
                  value={money(payments.data.totalCollectedAmount)}
                />
                <SummaryCard
                  label="Reversed Payments"
                  value={String(payments.data.reversedPaymentCount)}
                />
                <SummaryCard
                  label="Total Reversed Amount"
                  value={money(payments.data.totalReversedAmount)}
                />
              </div>

              <div className="grid gap-6 xl:grid-cols-2">
                <section className="card overflow-hidden">
                  <div className="border-b border-slate-100 px-5 py-4">
                    <h2 className="text-sm font-semibold">
                      Collections by payment method
                    </h2>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>Method</th>
                          <th>Completed Count</th>
                          <th>Collected Amount</th>
                          <th>Share</th>
                        </tr>
                      </thead>
                      <tbody>
                        {payments.data.byMethod
                          .filter(
                            (m) =>
                              !q ||
                              m.method.toLowerCase().includes(q) ||
                              human(m.method).toLowerCase().includes(q),
                          )
                          .map((m) => (
                            <tr key={m.method}>
                              <td className="font-medium">{human(m.method)}</td>
                              <td>{m.count}</td>
                              <td className="font-semibold">
                                {money(m.amount)}
                              </td>
                              <td>{m.sharePercent}%</td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                </section>

                <section className="card overflow-hidden">
                  <div className="border-b border-slate-100 px-5 py-4">
                    <h2 className="text-sm font-semibold">
                      Payments over time ({human(payments.data.granularity)})
                    </h2>
                  </div>
                  {!payments.data.series.length ? (
                    <Empty
                      title="No payments in selected period"
                      description="Recorded payments will appear here by period."
                    />
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="table">
                        <thead>
                          <tr>
                            <th>Period</th>
                            <th>Completed Count</th>
                            <th>Collected</th>
                            <th>Reversed</th>
                          </tr>
                        </thead>
                        <tbody>
                          {payments.data.series
                            .filter(
                              (pt) =>
                                !q ||
                                pt.period.toLowerCase().includes(q) ||
                                pt.label.toLowerCase().includes(q),
                            )
                            .map((pt) => (
                              <tr key={pt.period}>
                                <td className="font-medium">{pt.label}</td>
                                <td>{pt.paymentCount}</td>
                                <td className="font-semibold text-teal-700">
                                  {money(pt.collectedAmount)}
                                </td>
                                <td className="text-slate-500">
                                  {money(pt.reversedAmount)}
                                </td>
                              </tr>
                            ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              </div>
            </>
          )}
        </>
      )}

      {/* Tab 3: Subscription & MRR Summary */}
      {tab === "subscriptions" && (
        <>
          {subscriptions.isPending ? (
            <Loading />
          ) : subscriptions.error ? (
            <ErrorState
              error={subscriptions.error}
              retry={() => subscriptions.refetch()}
            />
          ) : (
            <>
              <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
                <SummaryCard
                  label="Total Subscriptions"
                  value={String(subscriptions.data.totalSubscriptions)}
                />
                <SummaryCard
                  label="Active Subscriptions"
                  value={String(subscriptions.data.activeSubscriptions)}
                />
                <SummaryCard
                  label="Paused Subscriptions"
                  value={String(subscriptions.data.pausedSubscriptions)}
                />
                <SummaryCard
                  label="Active MRR"
                  value={money(subscriptions.data.mrr)}
                />
                <SummaryCard
                  label="Active ARR"
                  value={money(subscriptions.data.arr)}
                />
                <SummaryCard
                  label="Paused MRR (On Hold)"
                  value={money(subscriptions.data.pausedMrr)}
                />
              </div>

              <section className="card mb-6 overflow-hidden">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="text-sm font-semibold">
                    Subscription & MRR breakdown by plan
                  </h2>
                </div>
                {!subscriptions.data.byPlan.length ? (
                  <Empty
                    title="No subscription plans in range"
                    description="Create subscriptions to view plan-level MRR and ARR."
                  />
                ) : (
                  <div className="overflow-x-auto">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>Plan</th>
                          <th>Billing Frequency</th>
                          <th>Active</th>
                          <th>Paused</th>
                          <th>Closed</th>
                          <th>Total</th>
                          <th>Active MRR</th>
                          <th>Active ARR</th>
                        </tr>
                      </thead>
                      <tbody>
                        {subscriptions.data.byPlan
                          .filter(
                            (p) =>
                              !q ||
                              p.planName.toLowerCase().includes(q) ||
                              p.billingPeriod.toLowerCase().includes(q),
                          )
                          .map((p) => (
                            <tr key={p.planId}>
                              <td className="font-medium">
                                <Link
                                  href={`/plans/${p.planId}`}
                                  className="text-violet-700 hover:underline"
                                >
                                  {p.planName}
                                </Link>
                              </td>
                              <td>{human(p.billingPeriod)}</td>
                              <td>{p.activeCount}</td>
                              <td>{p.pausedCount}</td>
                              <td>{p.closedCount}</td>
                              <td>{p.totalCount}</td>
                              <td className="font-semibold text-violet-700">
                                {money(p.mrr)}
                              </td>
                              <td className="font-semibold">{money(p.arr)}</td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>

              <section className="card overflow-hidden">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="text-sm font-semibold">
                    Breakdown by billing frequency
                  </h2>
                </div>
                <div className="overflow-x-auto">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Billing Frequency</th>
                        <th>Active</th>
                        <th>Paused</th>
                        <th>Total</th>
                        <th>Normalized Active MRR</th>
                        <th>Normalized Active ARR</th>
                      </tr>
                    </thead>
                    <tbody>
                      {subscriptions.data.byBillingFrequency.map((f) => (
                        <tr key={f.billingPeriod}>
                          <td className="font-medium">
                            {human(f.billingPeriod)}
                          </td>
                          <td>{f.activeCount}</td>
                          <td>{f.pausedCount}</td>
                          <td>{f.totalCount}</td>
                          <td className="font-semibold text-violet-700">
                            {money(f.mrr)}
                          </td>
                          <td className="font-semibold">{money(f.arr)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </>
          )}
        </>
      )}

      {/* Tab 4: Outstanding & Overdue Receivables */}
      {tab === "receivables" && (
        <>
          {receivables.isPending ? (
            <Loading />
          ) : receivables.error ? (
            <ErrorState
              error={receivables.error}
              retry={() => receivables.refetch()}
            />
          ) : (
            <>
              <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <SummaryCard
                  label="Total Open Receivables"
                  value={money(receivables.data.totalOutstandingAmount)}
                />
                <SummaryCard
                  label="Current (Not Yet Due)"
                  value={money(receivables.data.currentNotDueAmount)}
                />
                <SummaryCard
                  label="Total Overdue Balance"
                  value={money(receivables.data.totalOverdueAmount)}
                />
                <SummaryCard
                  label="Overdue Invoice Count"
                  value={String(receivables.data.overdueInvoiceCount)}
                />
              </div>

              <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {receivables.data.agingBuckets.map((b) => (
                  <div key={b.bucket} className="card p-4">
                    <p className="text-xs font-medium text-slate-500">
                      {b.label}
                    </p>
                    <p className="mt-1.5 text-xl font-semibold text-rose-700">
                      {money(b.amount)}
                    </p>
                    <p className="mt-1 text-[11px] text-slate-400">
                      {b.invoiceCount} invoice{b.invoiceCount === 1 ? "" : "s"}
                    </p>
                  </div>
                ))}
              </div>

              <section className="card overflow-hidden">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="text-sm font-semibold">
                    Overdue invoices schedule (as of {receivables.data.asOfDate})
                  </h2>
                </div>
                {!receivables.data.topOverdueInvoices.length ? (
                  <Empty
                    title="Zero overdue invoices"
                    description="All confirmed invoices are either paid or within their payment terms."
                  />
                ) : (
                  <div className="overflow-x-auto">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>Invoice</th>
                          <th>Customer</th>
                          <th>Plan</th>
                          <th>Due Date</th>
                          <th>Days Overdue</th>
                          <th>Bucket</th>
                          <th>Total</th>
                          <th>Paid</th>
                          <th>Balance Due</th>
                          <th />
                        </tr>
                      </thead>
                      <tbody>
                        {receivables.data.topOverdueInvoices
                          .filter(
                            (inv) =>
                              !q ||
                              inv.invoiceNumber.toLowerCase().includes(q) ||
                              inv.customerName.toLowerCase().includes(q) ||
                              inv.planName.toLowerCase().includes(q),
                          )
                          .map((inv) => (
                            <tr key={inv.invoiceId}>
                              <td>
                                <Link
                                  href={`/invoices/${inv.invoiceId}`}
                                  className="font-medium text-violet-700 hover:underline"
                                >
                                  {inv.invoiceNumber}
                                </Link>
                                <p className="mt-0.5 text-[10px] text-slate-400">
                                  {inv.subscriptionNumber}
                                </p>
                              </td>
                              <td>
                                <span className="font-medium">
                                  {inv.customerName}
                                </span>
                                <p className="mt-0.5 text-[10px] text-slate-400">
                                  {inv.customerEmail}
                                </p>
                              </td>
                              <td>{inv.planName}</td>
                              <td>{inv.dueDate}</td>
                              <td className="font-semibold text-rose-600">
                                {inv.daysOverdue}d
                              </td>
                              <td>
                                <span className="rounded-md bg-rose-50 px-2 py-0.5 text-[10px] font-semibold text-rose-700">
                                  {inv.agingBucket} days
                                </span>
                              </td>
                              <td>{money(inv.total)}</td>
                              <td>{money(inv.amountPaid)}</td>
                              <td className="font-semibold text-rose-700">
                                {money(inv.balanceDue)}
                              </td>
                              <td>
                                <Link
                                  aria-label={`View ${inv.invoiceNumber}`}
                                  href={`/invoices/${inv.invoiceId}`}
                                >
                                  <ArrowUpRight className="size-4 text-slate-400" />
                                </Link>
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
        </>
      )}
    </>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="card p-4">
      <p className="text-[11px] font-medium text-slate-500">{label}</p>
      <p className="mt-1.5 text-lg font-semibold text-slate-900">{value}</p>
    </div>
  );
}
