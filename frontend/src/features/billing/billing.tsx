"use client";
import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowUpRight,
  CalendarClock,
  Play,
  RotateCcw,
} from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage, queryString } from "@/lib/api";
import { human, money } from "@/lib/utils";
import type { BillingRun, Page, Subscription, User } from "@/lib/types";
import {
  Badge,
  DetailValue,
  Empty,
  ErrorState,
  FormField,
  Heading,
  Loading,
  Pagination,
  SearchField,
} from "@/components/shared";
import { Button } from "@/components/ui/button";

export function BillingDashboard({ user }: { user: User }) {
  const [billingDate, setBillingDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [duePage, setDuePage] = useState(0);
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [runPage, setRunPage] = useState(0);
  const client = useQueryClient();

  const due = useQuery({
    queryKey: ["billing-due", { billingDate, duePage }],
    queryFn: ({ signal }) =>
      api<Page<Subscription>>(
        `/billing/due${queryString({ billingDate, page: duePage, size: 10 })}`,
        { signal },
      ),
  });

  const runs = useQuery({
    queryKey: ["billing-runs", { q, status, runPage }],
    queryFn: ({ signal }) =>
      api<Page<BillingRun>>(
        `/billing/runs${queryString({ q, status, page: runPage, size: 10 })}`,
        { signal },
      ),
  });

  const triggerRun = useMutation({
    mutationFn: (payload: {
      billingDate: string;
      idempotencyKey?: string;
      subscriptionId?: number;
    }) =>
      api<BillingRun>("/billing/run", {
        method: "POST",
        body: payload,
      }),
    onSuccess: (result) => {
      client.invalidateQueries({ queryKey: ["billing-due"] });
      client.invalidateQueries({ queryKey: ["billing-runs"] });
      client.invalidateQueries({ queryKey: ["invoices"] });
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      client.invalidateQueries({ queryKey: ["discounts"] });
      toast.success(
        `Billing run #${result.id} completed: ${result.invoicesGenerated} invoice(s) generated.`,
      );
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const retryRun = useMutation({
    mutationFn: (runId: number) =>
      api<BillingRun>(`/billing/runs/${runId}/retry`, {
        method: "POST",
        body: {},
      }),
    onSuccess: (result) => {
      client.invalidateQueries({ queryKey: ["billing-due"] });
      client.invalidateQueries({ queryKey: ["billing-runs"] });
      client.invalidateQueries({ queryKey: ["invoices"] });
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      toast.success(
        `Retry run #${result.id} finished (${result.invoicesGenerated} invoiced).`,
      );
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <>
      <Heading
        eyebrow="Recurring billing engine"
        title="Billing & execution"
        description="Identify subscriptions due for billing, generate invoices with tax and discount rules, and track execution history."
      />

      <section className="card mb-7 p-6">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-semibold">Billing execution controls</h2>
            <p className="mt-1 text-xs text-slate-400">
              Evaluates active subscriptions due on or before the target billing
              date. Idempotency and row locks prevent duplicate invoices.
            </p>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-lg bg-violet-50 px-3 py-1.5 text-xs font-medium text-violet-700">
            <CalendarClock size={14} />
            Daily, Weekly, Monthly & Yearly cycles
          </span>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField
            label="Billing as-of date"
            hint="Subscriptions with next billing date ≤ this date are processed."
          >
            <input
              type="date"
              className="input"
              value={billingDate}
              onChange={(e) => {
                setBillingDate(e.target.value);
                setDuePage(0);
              }}
            />
          </FormField>
          <FormField
            label="Idempotency key (optional)"
            hint="Leave blank to auto-generate a unique execution key."
          >
            <input
              className="input"
              placeholder="e.g. MONTHLY-BATCH-2026-10"
              value={idempotencyKey}
              onChange={(e) => setIdempotencyKey(e.target.value)}
            />
          </FormField>
          <div className="flex items-end pb-1">
            {user.role === "ADMIN" ? (
              <Button
                className="w-full"
                disabled={triggerRun.isPending || !billingDate}
                onClick={() =>
                  triggerRun.mutate({
                    billingDate,
                    idempotencyKey: idempotencyKey.trim() || undefined,
                  })
                }
              >
                <Play />
                {triggerRun.isPending
                  ? "Running billing…"
                  : "Run recurring billing"}
              </Button>
            ) : (
              <p className="text-xs text-slate-500">
                Only administrators can trigger manual billing runs.
              </p>
            )}
          </div>
        </div>
      </section>

      <section className="card mb-7 overflow-hidden">
        <div className="border-b border-slate-100 p-5">
          <h2 className="font-semibold">
            Subscriptions due as of {billingDate || "today"}
          </h2>
          <p className="mt-1 text-xs text-slate-400">
            Active subscriptions with next billing date on or before{" "}
            {billingDate}.
          </p>
        </div>
        {due.isPending ? (
          <div className="p-5">
            <Loading />
          </div>
        ) : due.error ? (
          <div className="p-5">
            <ErrorState error={due.error} retry={() => due.refetch()} />
          </div>
        ) : !due.data.content.length ? (
          <Empty
            title="No subscriptions currently due"
            description={`All active subscriptions are billed past ${billingDate}, or no active subscriptions match this date.`}
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Subscription</th>
                    <th>Customer</th>
                    <th>Plan</th>
                    <th>Cycle</th>
                    <th>Next billing</th>
                    <th>Expiry</th>
                    <th>Base total</th>
                    {user.role === "ADMIN" && <th className="!text-right">Action</th>}
                  </tr>
                </thead>
                <tbody>
                  {due.data.content.map((s) => (
                    <tr key={s.id}>
                      <td>
                        <Link href={`/subscriptions/${s.id}`}>{s.number}</Link>
                      </td>
                      <td className="text-slate-500">{s.customerName}</td>
                      <td className="text-slate-500">{s.planName}</td>
                      <td>
                        <Badge value={s.billingPeriod || "MONTHLY"} />
                      </td>
                      <td className="font-medium text-violet-700">
                        {s.nextBillingDate || s.startDate}
                      </td>
                      <td className="text-slate-500">{s.expiryDate}</td>
                      <td className="font-medium">{money(s.total)}</td>
                      {user.role === "ADMIN" && (
                        <td className="text-right">
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={triggerRun.isPending}
                            onClick={() =>
                              triggerRun.mutate({
                                billingDate,
                                subscriptionId: s.id,
                              })
                            }
                          >
                            <Play />
                            Bill now
                          </Button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={duePage}
              totalPages={due.data.totalPages}
              totalElements={due.data.totalElements}
              onChange={setDuePage}
            />
          </>
        )}
      </section>

      <section className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-5">
          <div>
            <h2 className="font-semibold">Billing execution history</h2>
            <p className="mt-1 text-xs text-slate-400">
              Audit trail of manual, scheduled, and retry billing executions.
            </p>
          </div>
          <SearchField
            value={q}
            onChange={(v) => {
              setQ(v);
              setRunPage(0);
            }}
            placeholder="Search key or actor…"
          />
          <select
            aria-label="Filter run status"
            className="input sm:ml-auto sm:!w-52"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setRunPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option value="COMPLETED">Completed</option>
            <option value="COMPLETED_WITH_ERRORS">Completed with errors</option>
            <option value="FAILED">Failed</option>
            <option value="RUNNING">Running</option>
          </select>
        </div>
        {runs.isPending ? (
          <div className="p-5">
            <Loading />
          </div>
        ) : runs.error ? (
          <div className="p-5">
            <ErrorState error={runs.error} retry={() => runs.refetch()} />
          </div>
        ) : !runs.data.content.length ? (
          <Empty
            title="No billing runs yet"
            description="Trigger a billing run above to generate invoices and record execution history."
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Run</th>
                    <th>Billing date</th>
                    <th>Trigger</th>
                    <th>Actor</th>
                    <th>Status</th>
                    <th>Evaluated</th>
                    <th>Invoiced</th>
                    <th>Skipped</th>
                    <th>Failed</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.data.content.map((r) => (
                    <tr key={r.id}>
                      <td>
                        <Link href={`/billing/${r.id}`}>Run #{r.id}</Link>
                        <p className="mt-1 text-[10px] text-slate-400">
                          {r.idempotencyKey}
                        </p>
                      </td>
                      <td className="text-slate-500">{r.billingDate}</td>
                      <td className="text-slate-500">{human(r.triggerType)}</td>
                      <td className="text-slate-500">{r.triggeredBy}</td>
                      <td>
                        <Badge value={r.status} />
                      </td>
                      <td>{r.totalEvaluated}</td>
                      <td className="font-semibold text-teal-600">
                        {r.invoicesGenerated}
                      </td>
                      <td className="text-slate-500">{r.skippedCount}</td>
                      <td
                        className={
                          r.failedCount > 0
                            ? "font-semibold text-rose-600"
                            : "text-slate-500"
                        }
                      >
                        {r.failedCount}
                      </td>
                      <td>
                        <div className="flex items-center gap-2">
                          {user.role === "ADMIN" && r.failedCount > 0 && (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={retryRun.isPending}
                              onClick={() => retryRun.mutate(r.id)}
                            >
                              <RotateCcw />
                              Retry
                            </Button>
                          )}
                          <Link
                            href={`/billing/${r.id}`}
                            aria-label={`View run ${r.id}`}
                          >
                            <ArrowUpRight className="size-4 text-slate-400" />
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={runPage}
              totalPages={runs.data.totalPages}
              totalElements={runs.data.totalElements}
              onChange={setRunPage}
            />
          </>
        )}
      </section>
    </>
  );
}

export function BillingRunDetail({ id, user }: { id: string; user: User }) {
  const client = useQueryClient();
  const result = useQuery({
    queryKey: ["billing-runs", id],
    queryFn: ({ signal }) => api<BillingRun>(`/billing/runs/${id}`, { signal }),
  });

  const retryRun = useMutation({
    mutationFn: () =>
      api<BillingRun>(`/billing/runs/${id}/retry`, {
        method: "POST",
        body: {},
      }),
    onSuccess: (retried) => {
      client.invalidateQueries({ queryKey: ["billing-runs"] });
      client.invalidateQueries({ queryKey: ["invoices"] });
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      toast.success(
        `Retry run #${retried.id} completed (${retried.invoicesGenerated} invoiced).`,
      );
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const r = result.data;

  return (
    <>
      <Link
        href="/billing"
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to billing execution
      </Link>
      <Heading
        eyebrow="Billing execution details"
        title={`Billing run #${r.id}`}
        description={`Executed for ${r.billingDate} · ${r.idempotencyKey}`}
        action={
          user.role === "ADMIN" &&
          r.failedCount > 0 && (
            <Button
              disabled={retryRun.isPending}
              onClick={() => retryRun.mutate()}
            >
              <RotateCcw />
              {retryRun.isPending ? "Retrying…" : "Retry failed items"}
            </Button>
          )
        }
      />
      <section className="card mb-6 p-6">
        <div className="mb-7 flex items-center justify-between">
          <h2 className="font-semibold">Execution summary</h2>
          <Badge value={r.status} />
        </div>
        <dl className="grid gap-7 sm:grid-cols-4">
          <DetailValue label="Billing date">{r.billingDate}</DetailValue>
          <DetailValue label="Trigger type">{human(r.triggerType)}</DetailValue>
          <DetailValue label="Triggered by">{r.triggeredBy}</DetailValue>
          <DetailValue label="Idempotency key">{r.idempotencyKey}</DetailValue>
          <DetailValue label="Total evaluated">
            {String(r.totalEvaluated)}
          </DetailValue>
          <DetailValue label="Invoices generated">
            {String(r.invoicesGenerated)}
          </DetailValue>
          <DetailValue label="Skipped / already billed">
            {String(r.skippedCount)}
          </DetailValue>
          <DetailValue label="Expired & closed">
            {String(r.expiredClosedCount)}
          </DetailValue>
        </dl>
        {r.errorSummary && (
          <div className="mt-6 rounded-xl border border-rose-100 bg-rose-50 p-4 text-xs text-rose-700">
            <strong>Error summary:</strong> {r.errorSummary}
          </div>
        )}
      </section>

      <section className="card overflow-hidden">
        <div className="border-b border-slate-100 p-5">
          <h2 className="font-semibold">Subscription items</h2>
          <p className="mt-1 text-xs text-slate-400">
            Per-subscription billing outcome for this execution.
          </p>
        </div>
        {!r.items.length ? (
          <Empty
            title="No candidate subscriptions"
            description="No subscriptions required billing evaluation on this date."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>Subscription</th>
                  <th>Period start</th>
                  <th>Status</th>
                  <th>Attempt</th>
                  <th>Invoice</th>
                  <th>Message</th>
                </tr>
              </thead>
              <tbody>
                {r.items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <Link href={`/subscriptions/${item.subscriptionId}`}>
                        {item.subscriptionNumber}
                      </Link>
                    </td>
                    <td className="text-slate-500">
                      {item.billingPeriodStart}
                    </td>
                    <td>
                      <Badge value={item.status} />
                    </td>
                    <td>#{item.attemptCount}</td>
                    <td>
                      {item.invoiceId ? (
                        <Link href={`/invoices/${item.invoiceId}`}>
                          {item.invoiceNumber || `Invoice #${item.invoiceId}`}
                        </Link>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="text-xs text-slate-500">
                      {item.message || "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
