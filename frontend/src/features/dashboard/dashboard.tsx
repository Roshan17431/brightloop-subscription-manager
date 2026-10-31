"use client";
import { useState } from "react";
import Link from "next/link";
import dynamic from "next/dynamic";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  AlertTriangle,
  ArrowUpRight,
  BarChart3,
  CalendarClock,
  CheckCircle2,
  Clock,
  CreditCard,
  Download,
  FileSpreadsheet,
  FileText,
  Filter,
  PauseCircle,
  PlayCircle,
  RefreshCw,
  RotateCcw,
  TrendingUp,
  Wallet,
  XCircle,
} from "lucide-react";
import { api, errorMessage, queryString } from "@/lib/api";
import { human, money } from "@/lib/utils";
import {
  billingPeriods,
  granularities,
  type AnalyticsFilters,
  type AnalyticsOverview,
  type Customer,
  type CustomerPortalOverview,
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
  NewButton,
} from "@/components/shared";
import { Button } from "@/components/ui/button";

const RevenueTrendChart = dynamic(
  () => import("./analytics-charts").then((m) => m.RevenueTrendChart),
  {
    ssr: false,
    loading: () => <div className="h-64 animate-pulse rounded-lg bg-slate-50" />,
  },
);
const InvoicedVsCollectedChart = dynamic(
  () => import("./analytics-charts").then((m) => m.InvoicedVsCollectedChart),
  {
    ssr: false,
    loading: () => <div className="h-64 animate-pulse rounded-lg bg-slate-50" />,
  },
);
const PaymentMethodsChart = dynamic(
  () => import("./analytics-charts").then((m) => m.PaymentMethodsChart),
  {
    ssr: false,
    loading: () => <div className="h-64 animate-pulse rounded-lg bg-slate-50" />,
  },
);
const SubscriptionStatusChart = dynamic(
  () => import("./analytics-charts").then((m) => m.SubscriptionStatusChart),
  {
    ssr: false,
    loading: () => <div className="h-64 animate-pulse rounded-lg bg-slate-50" />,
  },
);
const MrrByPlanChart = dynamic(
  () => import("./analytics-charts").then((m) => m.MrrByPlanChart),
  {
    ssr: false,
    loading: () => <div className="h-64 animate-pulse rounded-lg bg-slate-50" />,
  },
);
const OverdueAgingChart = dynamic(
  () => import("./analytics-charts").then((m) => m.OverdueAgingChart),
  {
    ssr: false,
    loading: () => <div className="h-64 animate-pulse rounded-lg bg-slate-50" />,
  },
);

export function Dashboard({ user }: { user: User }) {
  if (user.role === "CUSTOMER") {
    return <CustomerPortalDashboard user={user} />;
  }
  return <ExecutiveDashboard user={user} />;
}

function ExecutiveDashboard({ user }: { user: User }) {
  const firstName = user.name.split(" ")[0];
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
    windowDays: filters.windowDays || 30,
  });

  const overview = useQuery({
    queryKey: ["analytics", "overview", qs],
    queryFn: ({ signal }) =>
      api<AnalyticsOverview>(`/analytics/overview${qs}`, { signal }),
  });

  const revenue = useQuery({
    queryKey: ["analytics", "revenue", qs],
    queryFn: ({ signal }) =>
      api<RevenueAnalytics>(`/analytics/revenue${qs}`, { signal }),
  });

  const subAnalytics = useQuery({
    queryKey: ["analytics", "subscriptions", qs],
    queryFn: ({ signal }) =>
      api<SubscriptionAnalytics>(`/analytics/subscriptions${qs}`, { signal }),
  });

  const payAnalytics = useQuery({
    queryKey: ["analytics", "payments", qs],
    queryFn: ({ signal }) =>
      api<PaymentAnalytics>(`/analytics/payments${qs}`, { signal }),
  });

  const receivables = useQuery({
    queryKey: ["analytics", "receivables", qs],
    queryFn: ({ signal }) =>
      api<ReceivablesAnalytics>(`/analytics/receivables${qs}`, { signal }),
  });

  const plans = useQuery({
    queryKey: ["plans", "filter-list"],
    queryFn: ({ signal }) => api<Page<Plan>>("/plans?size=100", { signal }),
  });

  const customers = useQuery({
    queryKey: ["customers", "filter-list"],
    queryFn: ({ signal }) =>
      api<Page<Customer>>("/customers?size=100", { signal }),
  });

  const ov = overview.data;

  function resetFilters() {
    setFilters({
      fromDate: "",
      toDate: "",
      granularity: "MONTHLY",
      customerId: "",
      planId: "",
      billingPeriod: "",
      windowDays: 30,
    });
  }

  return (
    <>
      <Heading
        eyebrow="Executive financial & subscription intelligence"
        title={`Good to see you, ${firstName}`}
        description="Real-time recurring revenue, receivables aging, payment velocity, and subscription lifecycle metrics."
        action={
          <div className="flex flex-wrap items-center gap-2.5">
            <Button asChild variant="outline">
              <Link href="/reports">
                <BarChart3 size={15} />
                Financial reports & CSV
              </Link>
            </Button>
            <NewButton href="/subscriptions/new">New subscription</NewButton>
          </div>
        }
      />

      {/* Filter Bar */}
      <section
        aria-label="Analytics filters"
        className="card mb-6 p-4 sm:p-5"
      >
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-700">
            <Filter size={14} className="text-violet-600" />
            Filter executive metrics
          </div>
          <button
            type="button"
            onClick={resetFilters}
            className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-violet-600"
          >
            <RotateCcw size={12} />
            Reset filters
          </button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
          <div>
            <label
              htmlFor="dash-from-date"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              From date
            </label>
            <input
              id="dash-from-date"
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
              htmlFor="dash-to-date"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              To date
            </label>
            <input
              id="dash-to-date"
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
              htmlFor="dash-granularity"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Trend granularity
            </label>
            <select
              id="dash-granularity"
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
              htmlFor="dash-billing-period"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Billing frequency
            </label>
            <select
              id="dash-billing-period"
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
              htmlFor="dash-plan"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Plan
            </label>
            <select
              id="dash-plan"
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
              htmlFor="dash-customer"
              className="mb-1 block text-[11px] font-medium text-slate-500"
            >
              Customer
            </label>
            <select
              id="dash-customer"
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
      </section>

      {overview.error ? (
        <div className="mb-6">
          <ErrorState
            error={overview.error}
            retry={() => overview.refetch()}
          />
        </div>
      ) : null}

      {/* Primary Financial KPI Row (MRR, ARR, Invoiced, Collected, Outstanding, Overdue) */}
      <div className="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-6">
        <KpiCard
          title="Monthly Recurring (MRR)"
          value={ov ? money(ov.mrr) : undefined}
          loading={overview.isPending}
          subtitle={
            ov && Number(ov.pausedMrr) > 0
              ? `Paused on hold: ${money(ov.pausedMrr)}`
              : "Active pre-tax normalized"
          }
          icon={TrendingUp}
          tone="violet"
          href="/subscriptions?state=ACTIVE"
        />
        <KpiCard
          title="Annual Recurring (ARR)"
          value={ov ? money(ov.arr) : undefined}
          loading={overview.isPending}
          subtitle="MRR × 12 annualized"
          icon={BarChart3}
          tone="violet"
          href="/reports"
        />
        <KpiCard
          title="Invoiced Revenue"
          value={ov ? money(ov.invoicedRevenue) : undefined}
          loading={overview.isPending}
          subtitle={
            ov
              ? `Pre-tax ${money(ov.invoicedSubtotal)} · Tax ${money(ov.invoicedTax)}`
              : "Confirmed & paid invoices"
          }
          icon={FileText}
          tone="teal"
          href="/invoices"
        />
        <KpiCard
          title="Collected Payments"
          value={ov ? money(ov.collectedPayments) : undefined}
          loading={overview.isPending}
          subtitle="Completed payments received"
          icon={Wallet}
          tone="teal"
          href="/payments"
        />
        <KpiCard
          title="Outstanding Receivables"
          value={ov ? money(ov.outstandingReceivables) : undefined}
          loading={overview.isPending}
          subtitle="Open confirmed balances"
          icon={Clock}
          tone="amber"
          href="/invoices?state=CONFIRMED"
        />
        <KpiCard
          title="Overdue Receivables"
          value={ov ? money(ov.overdueAmount) : undefined}
          loading={overview.isPending}
          subtitle={
            ov
              ? `${ov.overdueInvoiceCount} overdue invoice${ov.overdueInvoiceCount === 1 ? "" : "s"}`
              : "Past due date"
          }
          icon={AlertTriangle}
          tone={ov && ov.overdueInvoiceCount > 0 ? "rose" : "slate"}
          href="/invoices?overdue=true"
        />
      </div>

      {/* Subscription Lifecycle KPI Row */}
      <div className="mb-7 grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
        <StatPill
          label="Total subscriptions"
          value={ov?.totalSubscriptions}
          loading={overview.isPending}
          href="/subscriptions"
        />
        <StatPill
          label="Active subscriptions"
          value={ov?.activeSubscriptions}
          loading={overview.isPending}
          href="/subscriptions?state=ACTIVE"
          highlight="emerald"
        />
        <StatPill
          label="Paused subscriptions"
          value={ov?.pausedSubscriptions}
          loading={overview.isPending}
          href="/subscriptions?state=PAUSED"
          highlight="amber"
        />
        <StatPill
          label="Closed subscriptions"
          value={ov?.closedSubscriptions}
          loading={overview.isPending}
          href="/subscriptions?state=CLOSED"
        />
        <StatPill
          label="New subscriptions"
          value={ov?.newSubscriptions}
          loading={overview.isPending}
          href="/subscriptions"
          highlight="violet"
        />
        <StatPill
          label="Renewals / Expiring (30d)"
          value={
            ov
              ? `${ov.upcomingRenewalsCount} / ${ov.expiringSubscriptionsCount}`
              : undefined
          }
          loading={overview.isPending}
          href="/billing"
        />
      </div>

      {/* Row 1 Charts: Revenue Trend & Invoiced vs Collected */}
      <div className="mb-7 grid gap-6 xl:grid-cols-2">
        <section className="card p-6">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold">
                Revenue & cash collection trend
              </h2>
              <p className="mt-1 text-xs text-slate-400">
                Invoiced revenue vs. collected payments ({human(filters.granularity || "MONTHLY")})
              </p>
            </div>
            {revenue.data && (
              <span className="rounded-md bg-teal-50 px-2.5 py-1 text-[11px] font-semibold text-teal-700">
                Collection rate: {revenue.data.collectionRatePercent}%
              </span>
            )}
          </div>
          {revenue.error ? (
            <ErrorState error={revenue.error} retry={() => revenue.refetch()} />
          ) : revenue.isPending ? (
            <div className="h-64 animate-pulse rounded-lg bg-slate-50" />
          ) : !revenue.data.series.length ? (
            <Empty
              title="No revenue periods yet"
              description="Confirm invoices or record payments to populate the revenue trend chart."
            />
          ) : (
            <RevenueTrendChart data={revenue.data.series} />
          )}
        </section>

        <section className="card p-6">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold">
                Invoiced vs. collected vs. outstanding
              </h2>
              <p className="mt-1 text-xs text-slate-400">
                Period-by-period billing realization and open receivables
              </p>
            </div>
            <Link
              href="/reports"
              className="text-xs font-medium text-violet-600 hover:underline"
            >
              Full breakdown
            </Link>
          </div>
          {revenue.error ? (
            <ErrorState error={revenue.error} retry={() => revenue.refetch()} />
          ) : revenue.isPending ? (
            <div className="h-64 animate-pulse rounded-lg bg-slate-50" />
          ) : !revenue.data.series.length ? (
            <Empty
              title="No billing comparison data"
              description="No confirmed invoices or payments match the current filter range."
            />
          ) : (
            <InvoicedVsCollectedChart data={revenue.data.series} />
          )}
        </section>
      </div>

      {/* Row 2 Charts: Subscription Status, MRR by Plan, Payment Methods, Overdue Aging */}
      <div className="mb-7 grid gap-6 xl:grid-cols-2">
        <section className="card p-6">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold">
                Subscription status distribution
              </h2>
              <p className="mt-1 text-xs text-slate-400">
                Breakdown across draft, quotation, confirmed, active, paused, and closed states
              </p>
            </div>
            <span className="rounded-md border border-slate-200 px-2 py-1 text-[10px] text-slate-500">
              {subAnalytics.data?.totalSubscriptions ?? 0} total
            </span>
          </div>
          {subAnalytics.error ? (
            <ErrorState
              error={subAnalytics.error}
              retry={() => subAnalytics.refetch()}
            />
          ) : subAnalytics.isPending ? (
            <div className="h-64 animate-pulse rounded-lg bg-slate-50" />
          ) : (
            <SubscriptionStatusChart data={subAnalytics.data.byStatus} />
          )}
        </section>

        <section className="card p-6">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold">Active MRR by plan</h2>
              <p className="mt-1 text-xs text-slate-400">
                Normalized monthly recurring revenue contribution per subscription plan
              </p>
            </div>
            <Link
              href="/plans"
              className="text-xs font-medium text-violet-600 hover:underline"
            >
              Manage plans
            </Link>
          </div>
          {subAnalytics.error ? (
            <ErrorState
              error={subAnalytics.error}
              retry={() => subAnalytics.refetch()}
            />
          ) : subAnalytics.isPending ? (
            <div className="h-64 animate-pulse rounded-lg bg-slate-50" />
          ) : !subAnalytics.data.byPlan.length ? (
            <Empty
              title="No active plans with subscriptions"
              description="Create subscriptions to compare recurring revenue across plans."
            />
          ) : (
            <MrrByPlanChart data={subAnalytics.data.byPlan} />
          )}
        </section>

        <section className="card p-6">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold">
                Payments by payment method
              </h2>
              <p className="mt-1 text-xs text-slate-400">
                Completed collections across bank transfer, UPI, card, cash, and manual methods
              </p>
            </div>
            <span className="rounded-md border border-slate-200 px-2 py-1 text-[10px] text-slate-500">
              {payAnalytics.data?.completedPaymentCount ?? 0} payments
            </span>
          </div>
          {payAnalytics.error ? (
            <ErrorState
              error={payAnalytics.error}
              retry={() => payAnalytics.refetch()}
            />
          ) : payAnalytics.isPending ? (
            <div className="h-64 animate-pulse rounded-lg bg-slate-50" />
          ) : (
            <PaymentMethodsChart data={payAnalytics.data.byMethod} />
          )}
        </section>

        <section className="card p-6">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold">
                Overdue receivables aging
              </h2>
              <p className="mt-1 text-xs text-slate-400">
                Unpaid balance on confirmed invoices past due date (1–30, 31–60, 61–90, 90+ days)
              </p>
            </div>
            <span className="rounded-md bg-rose-50 px-2.5 py-1 text-[11px] font-semibold text-rose-700">
              {receivables.data ? money(receivables.data.totalOverdueAmount) : "—"} overdue
            </span>
          </div>
          {receivables.error ? (
            <ErrorState
              error={receivables.error}
              retry={() => receivables.refetch()}
            />
          ) : receivables.isPending ? (
            <div className="h-64 animate-pulse rounded-lg bg-slate-50" />
          ) : (
            <OverdueAgingChart data={receivables.data.agingBuckets} />
          )}
        </section>
      </div>

      {/* Operational Tables Row 1: Upcoming Renewals & Subscriptions Approaching Expiry */}
      <div className="mb-7 grid gap-6 xl:grid-cols-2">
        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold">
                Upcoming billing & renewals (next 30 days)
              </h2>
              <p className="mt-1 text-xs text-slate-400">
                Active subscriptions scheduled for recurring billing
              </p>
            </div>
            <Link
              href="/billing"
              className="text-xs font-medium text-violet-600 hover:underline"
            >
              Billing console
            </Link>
          </div>
          {overview.isPending ? (
            <TableSkeleton />
          ) : !ov?.upcomingRenewals.length ? (
            <Empty
              title="No upcoming renewals in window"
              description="No active subscriptions have a billing date in the next 30 days."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Subscription</th>
                    <th>Customer</th>
                    <th>Next Billing</th>
                    <th>MRR</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {ov.upcomingRenewals.map((r) => (
                    <tr key={r.subscriptionId}>
                      <td>
                        <Link
                          href={`/subscriptions/${r.subscriptionId}`}
                          className="font-medium text-violet-700 hover:underline"
                        >
                          {r.subscriptionNumber}
                        </Link>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          {r.planName} ({human(r.billingPeriod)})
                        </p>
                      </td>
                      <td>{r.customerName}</td>
                      <td>
                        <span className="font-medium">{r.nextBillingDate}</span>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          In {r.daysUntilBilling} day{r.daysUntilBilling === 1 ? "" : "s"}
                        </p>
                      </td>
                      <td className="font-medium">{money(r.mrrContribution)}</td>
                      <td>
                        <Link
                          aria-label={`View ${r.subscriptionNumber}`}
                          href={`/subscriptions/${r.subscriptionId}`}
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

        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold">
                Subscriptions approaching expiry
              </h2>
              <p className="mt-1 text-xs text-slate-400">
                Active and paused subscriptions expiring within 30 days
              </p>
            </div>
            <Link
              href="/subscriptions"
              className="text-xs font-medium text-violet-600 hover:underline"
            >
              View all
            </Link>
          </div>
          {overview.isPending ? (
            <TableSkeleton />
          ) : !ov?.expiringSubscriptions.length ? (
            <Empty
              title="No subscriptions expiring soon"
              description="No active or paused subscriptions expire within the next 30 days."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Subscription</th>
                    <th>Customer</th>
                    <th>Expiry Date</th>
                    <th>Renewal</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {ov.expiringSubscriptions.map((ex) => (
                    <tr key={ex.subscriptionId}>
                      <td>
                        <Link
                          href={`/subscriptions/${ex.subscriptionId}`}
                          className="font-medium text-violet-700 hover:underline"
                        >
                          {ex.subscriptionNumber}
                        </Link>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          {ex.planName}
                        </p>
                      </td>
                      <td>{ex.customerName}</td>
                      <td>
                        <span className="font-medium text-amber-700">
                          {ex.expiryDate}
                        </span>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          {ex.daysUntilExpiry} day{ex.daysUntilExpiry === 1 ? "" : "s"} left
                        </p>
                      </td>
                      <td>
                        <Badge
                          value={ex.renewalAllowed ? "ACTIVE" : "CLOSED"}
                        />
                      </td>
                      <td>
                        <Link
                          aria-label={`View ${ex.subscriptionNumber}`}
                          href={`/subscriptions/${ex.subscriptionId}`}
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
      </div>

      {/* Operational Tables Row 2: Recent Invoices & Recent Payments */}
      <div className="grid gap-6 xl:grid-cols-2">
        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold">Recent invoices</h2>
              <p className="mt-1 text-xs text-slate-400">
                Latest generated invoices and open balances
              </p>
            </div>
            <Link
              href="/invoices"
              className="text-xs font-medium text-violet-600 hover:underline"
            >
              All invoices
            </Link>
          </div>
          {overview.isPending ? (
            <TableSkeleton />
          ) : !ov?.recentInvoices.length ? (
            <Empty
              title="No invoices recorded"
              description="Run recurring billing to generate subscription invoices."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Invoice</th>
                    <th>Customer</th>
                    <th>State</th>
                    <th>Total</th>
                    <th>Balance Due</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {ov.recentInvoices.map((inv) => (
                    <tr key={inv.id}>
                      <td>
                        <Link
                          href={`/invoices/${inv.id}`}
                          className="font-medium text-violet-700 hover:underline"
                        >
                          {inv.invoiceNumber}
                        </Link>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          Due {inv.dueDate}
                        </p>
                      </td>
                      <td>{inv.customerName}</td>
                      <td>
                        <div className="flex items-center gap-1.5">
                          <Badge value={inv.state} />
                          {inv.overdue && (
                            <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[9px] font-semibold text-rose-700">
                              OVERDUE
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="font-medium">{money(inv.total)}</td>
                      <td
                        className={
                          Number(inv.balanceDue) > 0
                            ? "font-semibold text-amber-700"
                            : "text-slate-400"
                        }
                      >
                        {money(inv.balanceDue)}
                      </td>
                      <td>
                        <Link
                          aria-label={`View ${inv.invoiceNumber}`}
                          href={`/invoices/${inv.id}`}
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

        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold">Recent payments</h2>
              <p className="mt-1 text-xs text-slate-400">
                Latest customer payment collections and reconciliations
              </p>
            </div>
            <Link
              href="/payments"
              className="text-xs font-medium text-violet-600 hover:underline"
            >
              All payments
            </Link>
          </div>
          {overview.isPending ? (
            <TableSkeleton />
          ) : !ov?.recentPayments.length ? (
            <Empty
              title="No payments recorded"
              description="Record payments against confirmed invoices to track collections."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Customer</th>
                    <th>Method</th>
                    <th>Status</th>
                    <th>Amount</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {ov.recentPayments.map((pay) => (
                    <tr key={pay.id}>
                      <td>
                        <Link
                          href={`/payments/${pay.id}`}
                          className="font-medium text-violet-700 hover:underline"
                        >
                          {pay.paymentReference}
                        </Link>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          {pay.paymentDate} · {pay.invoiceNumber}
                        </p>
                      </td>
                      <td>{pay.customerName}</td>
                      <td className="text-xs text-slate-600">
                        {human(pay.method)}
                      </td>
                      <td>
                        <Badge value={pay.status} />
                      </td>
                      <td className="font-medium">{money(pay.amount)}</td>
                      <td>
                        <Link
                          aria-label={`View ${pay.paymentReference}`}
                          href={`/payments/${pay.id}`}
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
      </div>
    </>
  );
}

function CustomerPortalDashboard({ user }: { user: User }) {
  const client = useQueryClient();
  const firstName = user.name.split(" ")[0];

  const portal = useQuery({
    queryKey: ["portal", "overview"],
    queryFn: ({ signal }) =>
      api<CustomerPortalOverview>("/portal/overview", { signal }),
  });

  const lifecycleMutation = useMutation({
    mutationFn: ({
      id,
      action,
      payload,
    }: {
      id: number;
      action: "pause" | "resume" | "renew" | "close";
      payload?: Record<string, unknown>;
    }) =>
      api(`/subscriptions/${id}/${action}`, {
        method: "POST",
        body: JSON.stringify(payload || {}),
      }),
    onSuccess: () => {
      toast.success("Subscription updated");
      client.invalidateQueries({ queryKey: ["portal"] });
      client.invalidateQueries({ queryKey: ["subscriptions"] });
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  if (portal.isPending) {
    return (
      <div className="space-y-5">
        <div className="h-20 animate-pulse rounded-xl bg-slate-100" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {[1, 2, 3, 4].map((i) => (
            <div
              key={i}
              className="h-28 animate-pulse rounded-xl bg-slate-100"
            />
          ))}
        </div>
      </div>
    );
  }

  if (portal.error) {
    return <ErrorState error={portal.error} retry={() => portal.refetch()} />;
  }

  const data = portal.data;

  return (
    <>
      <Heading
        eyebrow="Customer self-service portal"
        title={`Welcome back, ${firstName}`}
        description="Manage your active subscriptions, upcoming billing dates, invoices, and payment history."
        action={
          <div className="flex items-center gap-2.5">
            <Button asChild variant="outline">
              <Link href="/quotations">
                <FileSpreadsheet size={15} />
                My quotations
              </Link>
            </Button>
            <NewButton href="/subscriptions/new">New subscription</NewButton>
          </div>
        }
      />

      {data.overdueInvoiceCount > 0 && (
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-rose-200 bg-rose-50/70 px-5 py-4 text-xs text-rose-900">
          <div className="flex items-center gap-3">
            <AlertTriangle className="size-5 shrink-0 text-rose-600" />
            <div>
              <p className="font-semibold">
                You have {data.overdueInvoiceCount} overdue invoice
                {data.overdueInvoiceCount === 1 ? "" : "s"} totaling{" "}
                {money(data.overdueBalance)}
              </p>
              <p className="mt-0.5 text-rose-700">
                Review your open invoices below or download PDF statements for your accounting team.
              </p>
            </div>
          </div>
          <Button asChild size="sm" variant="outline">
            <Link href="/invoices">View open invoices</Link>
          </Button>
        </div>
      )}

      {/* Customer Portal KPI Cards */}
      <div className="mb-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          title="Active Subscriptions"
          value={String(data.activeSubscriptionCount)}
          subtitle={`${data.pausedSubscriptionCount} paused · ${data.closedSubscriptionCount} closed`}
          icon={CreditCard}
          tone="violet"
          href="/subscriptions"
        />
        <KpiCard
          title="Monthly Recurring Spend"
          value={money(data.monthlyRecurringSpend)}
          subtitle="Pre-tax normalized monthly rate"
          icon={TrendingUp}
          tone="teal"
          href="/subscriptions"
        />
        <KpiCard
          title="Outstanding Balance"
          value={money(data.outstandingBalance)}
          subtitle={`${data.openInvoiceCount} open invoice${data.openInvoiceCount === 1 ? "" : "s"}`}
          icon={FileText}
          tone={Number(data.outstandingBalance) > 0 ? "amber" : "slate"}
          href="/invoices"
        />
        <KpiCard
          title="Next Billing Date"
          value={data.nextBillingDate || "None scheduled"}
          subtitle={`Total paid to date: ${money(data.totalPaidAmount)}`}
          icon={CalendarClock}
          tone="violet"
          href="/payments"
        />
      </div>

      {/* Customer Subscriptions & Self-Service Lifecycle Actions */}
      <section className="card mb-7 overflow-hidden">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold">
              My subscriptions & self-service controls
            </h2>
            <p className="mt-1 text-xs text-slate-400">
              View upcoming billing dates or pause, resume, and renew eligible plans
            </p>
          </div>
          <Link
            href="/subscriptions"
            className="text-xs font-medium text-violet-600 hover:underline"
          >
            All subscriptions
          </Link>
        </div>
        {!data.subscriptions.length ? (
          <Empty
            title="No subscriptions yet"
            description="Create your first subscription to start using Brightloop."
            action={
              <NewButton href="/subscriptions/new">
                Create subscription
              </NewButton>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>Subscription</th>
                  <th>Plan</th>
                  <th>State</th>
                  <th>Next Billing</th>
                  <th>Expiry Date</th>
                  <th>Cycle Total</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.subscriptions.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <Link
                        href={`/subscriptions/${s.id}`}
                        className="font-medium text-violet-700 hover:underline"
                      >
                        {s.number}
                      </Link>
                      <p className="mt-0.5 text-[10px] text-slate-400">
                        Started {s.startDate}
                      </p>
                    </td>
                    <td>
                      <span className="font-medium">{s.planName}</span>
                      {s.billingPeriod && (
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          {human(s.billingPeriod)}
                        </p>
                      )}
                    </td>
                    <td>
                      <Badge value={s.state} />
                    </td>
                    <td>
                      {s.state === "ACTIVE" && s.nextBillingDate
                        ? s.nextBillingDate
                        : "—"}
                    </td>
                    <td>{s.expiryDate}</td>
                    <td className="font-medium">{money(s.total)}</td>
                    <td>
                      <div className="flex flex-wrap items-center gap-1.5">
                        {s.state === "ACTIVE" && s.pausingAllowed && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={lifecycleMutation.isPending}
                            onClick={() =>
                              lifecycleMutation.mutate({
                                id: s.id,
                                action: "pause",
                                payload: { reason: "Paused via customer portal" },
                              })
                            }
                          >
                            <PauseCircle size={13} />
                            Pause
                          </Button>
                        )}
                        {s.state === "PAUSED" && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={lifecycleMutation.isPending}
                            onClick={() =>
                              lifecycleMutation.mutate({
                                id: s.id,
                                action: "resume",
                              })
                            }
                          >
                            <PlayCircle size={13} />
                            Resume
                          </Button>
                        )}
                        {(s.state === "ACTIVE" || s.state === "CLOSED") &&
                          s.renewalAllowed && (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={lifecycleMutation.isPending}
                              onClick={() =>
                                lifecycleMutation.mutate({
                                  id: s.id,
                                  action: "renew",
                                  payload: { periods: 1 },
                                })
                              }
                            >
                              <RefreshCw size={13} />
                              Renew
                            </Button>
                          )}
                        {s.state !== "CLOSED" &&
                          s.state !== "DRAFT" &&
                          s.earlyClosingAllowed && (
                            <Button
                              size="sm"
                              variant="ghost"
                              className="text-rose-600 hover:bg-rose-50 hover:text-rose-700"
                              disabled={lifecycleMutation.isPending}
                              onClick={() =>
                                lifecycleMutation.mutate({
                                  id: s.id,
                                  action: "close",
                                  payload: {
                                    reason: "Closed by customer in portal",
                                  },
                                })
                              }
                            >
                              <XCircle size={13} />
                              Close
                            </Button>
                          )}
                        <Button asChild size="sm" variant="ghost">
                          <Link href={`/subscriptions/${s.id}`}>Details</Link>
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Customer Recent Invoices & Recent Payments */}
      <div className="grid gap-6 xl:grid-cols-2">
        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold">Recent invoices</h2>
              <p className="mt-1 text-xs text-slate-400">
                Download PDF statements and check payment status
              </p>
            </div>
            <Link
              href="/invoices"
              className="text-xs font-medium text-violet-600 hover:underline"
            >
              All invoices
            </Link>
          </div>
          {!data.recentInvoices.length ? (
            <Empty
              title="No invoices yet"
              description="Invoices generated for your subscriptions will appear here."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Invoice</th>
                    <th>State</th>
                    <th>Total</th>
                    <th>Balance Due</th>
                    <th>PDF</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recentInvoices.map((inv) => (
                    <tr key={inv.id}>
                      <td>
                        <Link
                          href={`/invoices/${inv.id}`}
                          className="font-medium text-violet-700 hover:underline"
                        >
                          {inv.invoiceNumber}
                        </Link>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          Issued {inv.issueDate} · Due {inv.dueDate}
                        </p>
                      </td>
                      <td>
                        <div className="flex items-center gap-1.5">
                          <Badge value={inv.state} />
                          {inv.overdue && (
                            <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[9px] font-semibold text-rose-700">
                              OVERDUE
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="font-medium">{money(inv.total)}</td>
                      <td
                        className={
                          Number(inv.balanceDue) > 0
                            ? "font-semibold text-amber-700"
                            : "text-slate-400"
                        }
                      >
                        {money(inv.balanceDue)}
                      </td>
                      <td>
                        <a
                          href={`/api/v1/invoices/${inv.id}/pdf`}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                        >
                          <Download size={12} />
                          PDF
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold">Payment history</h2>
              <p className="mt-1 text-xs text-slate-400">
                Payments recorded and reconciled against your invoices
              </p>
            </div>
            <Link
              href="/payments"
              className="text-xs font-medium text-violet-600 hover:underline"
            >
              All payments
            </Link>
          </div>
          {!data.recentPayments.length ? (
            <Empty
              title="No recorded payments"
              description="Completed payments for your invoices will be listed here."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Payment Ref</th>
                    <th>Invoice</th>
                    <th>Method</th>
                    <th>Status</th>
                    <th>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recentPayments.map((pay) => (
                    <tr key={pay.id}>
                      <td>
                        <Link
                          href={`/payments/${pay.id}`}
                          className="font-medium text-violet-700 hover:underline"
                        >
                          {pay.paymentReference}
                        </Link>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          {pay.paymentDate}
                        </p>
                      </td>
                      <td>
                        <Link
                          href={`/invoices/${pay.invoiceId}`}
                          className="text-xs text-slate-600 hover:underline"
                        >
                          {pay.invoiceNumber}
                        </Link>
                      </td>
                      <td className="text-xs text-slate-600">
                        {human(pay.method)}
                      </td>
                      <td>
                        <Badge value={pay.status} />
                      </td>
                      <td className="font-medium">{money(pay.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </>
  );
}

function KpiCard({
  title,
  value,
  subtitle,
  loading,
  icon: Icon,
  tone,
  href,
}: {
  title: string;
  value?: string;
  subtitle: string;
  loading?: boolean;
  icon: React.ComponentType<{ size?: number }>;
  tone: "violet" | "teal" | "amber" | "rose" | "slate";
  href: string;
}) {
  const toneClasses = {
    violet: "bg-violet-50 text-violet-600",
    teal: "bg-teal-50 text-teal-600",
    amber: "bg-amber-50 text-amber-600",
    rose: "bg-rose-50 text-rose-600",
    slate: "bg-slate-100 text-slate-600",
  }[tone];

  return (
    <Link
      href={href}
      className="card group flex flex-col justify-between p-4 transition hover:border-violet-200"
    >
      <div className="mb-3 flex items-center justify-between">
        <span
          className={`flex size-8 items-center justify-center rounded-xl ${toneClasses}`}
        >
          <Icon size={16} />
        </span>
        <ArrowUpRight className="size-4 text-slate-300 group-hover:text-violet-500" />
      </div>
      <div>
        <p className="text-[11px] font-medium text-slate-500">{title}</p>
        <div className="mt-1 text-xl font-semibold tracking-tight text-slate-900">
          {loading ? (
            <span className="inline-block h-7 w-24 animate-pulse rounded bg-slate-100" />
          ) : (
            value ?? "—"
          )}
        </div>
        <p className="mt-1.5 truncate text-[10px] text-slate-400">{subtitle}</p>
      </div>
    </Link>
  );
}

function StatPill({
  label,
  value,
  loading,
  href,
  highlight,
}: {
  label: string;
  value?: number | string;
  loading?: boolean;
  href: string;
  highlight?: "emerald" | "amber" | "violet";
}) {
  const dotColor =
    highlight === "emerald"
      ? "bg-emerald-500"
      : highlight === "amber"
        ? "bg-amber-500"
        : highlight === "violet"
          ? "bg-violet-500"
          : "bg-slate-300";

  return (
    <Link
      href={href}
      className="card flex items-center justify-between px-4 py-3 transition hover:border-violet-200"
    >
      <div>
        <div className="flex items-center gap-1.5">
          <span className={`size-2 rounded-full ${dotColor}`} />
          <span className="text-[11px] font-medium text-slate-500">{label}</span>
        </div>
        <div className="mt-1 text-lg font-semibold text-slate-900">
          {loading ? (
            <span className="inline-block h-6 w-12 animate-pulse rounded bg-slate-100" />
          ) : value !== undefined ? (
            typeof value === "number" ? (
              value.toLocaleString()
            ) : (
              value
            )
          ) : (
            "—"
          )}
        </div>
      </div>
      <CheckCircle2 className="size-4 text-slate-200" />
    </Link>
  );
}

function TableSkeleton() {
  return (
    <div className="space-y-3 p-5">
      {[1, 2, 3].map((i) => (
        <div key={i} className="h-9 animate-pulse rounded bg-slate-50" />
      ))}
    </div>
  );
}
