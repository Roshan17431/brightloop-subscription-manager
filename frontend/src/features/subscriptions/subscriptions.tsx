"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  useMutation,
  useQuery,
  useQueries,
  useQueryClient,
} from "@tanstack/react-query";
import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  Ban,
  Check,
  ChevronRight,
  Pause,
  Pencil,
  Play,
  Plus,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage, queryString } from "@/lib/api";
import { human, money, adjustment } from "@/lib/utils";
import {
  states,
  subscriptionFilterStates,
  type BillingRun,
  type Customer,
  type Invoice,
  type Page,
  type Plan,
  type Product,
  type Subscription,
  type User,
} from "@/lib/types";
import {
  Badge,
  DetailValue,
  Empty,
  ErrorState,
  FormActions,
  FormField,
  Heading,
  Loading,
  NewButton,
  Pagination,
  SearchField,
} from "@/components/shared";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/dialog";
import { subscriptionSchema } from "./schemas";
export function SubscriptionList({ user }: { user: User }) {
  const [q, setQ] = useState("");
  const [state, setState] = useState("");
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ["subscriptions", { q, state, page }],
    queryFn: ({ signal }) =>
      api<Page<Subscription>>(
        `/subscriptions${queryString({ q, state, page, size: 10 })}`,
        { signal },
      ),
  });
  return (
    <>
      <Heading
        eyebrow={
          user.role === "CUSTOMER" ? "Your subscriptions" : "Business in motion"
        }
        title="Subscriptions"
        description={
          user.role === "CUSTOMER"
            ? "Your plans, progress, and subscription details in one place."
            : "From the first draft to an active relationship. Follow every step."
        }
        action={
          <NewButton href="/subscriptions/new">New subscription</NewButton>
        }
      />
      <section className="card overflow-hidden">
        <div className="flex flex-wrap gap-3 border-b border-slate-100 p-5">
          <SearchField
            value={q}
            onChange={(v) => {
              setQ(v);
              setPage(0);
            }}
            placeholder="Search subscriptions…"
          />
          <select
            aria-label="Filter subscription state"
            className="input sm:ml-auto sm:!w-44"
            value={state}
            onChange={(e) => {
              setState(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All states</option>
            {subscriptionFilterStates.map((s) => (
              <option key={s} value={s}>
                {human(s)}
              </option>
            ))}
          </select>
        </div>
        {result.isPending ? (
          <div className="p-5">
            <Loading />
          </div>
        ) : result.error ? (
          <ErrorState error={result.error} retry={() => result.refetch()} />
        ) : !result.data.content.length ? (
          <Empty
            title={
              q || state
                ? "No matching subscriptions"
                : "Your next relationship starts here"
            }
            description={
              q || state
                ? "Try another search or filter."
                : "Create a draft subscription, add your products, and take it from there."
            }
            action={
              <NewButton href="/subscriptions/new">
                Create subscription
              </NewButton>
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Subscription</th>
                    {user.role !== "CUSTOMER" && <th>Customer</th>}
                    <th>Plan</th>
                    <th>Start date</th>
                    <th>State</th>
                    <th>Amount</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((s) => (
                    <tr key={s.id}>
                      <td>
                        <Link href={`/subscriptions/${s.id}`}>{s.number}</Link>
                      </td>
                      {user.role !== "CUSTOMER" && (
                        <td className="text-slate-500">{s.customerName}</td>
                      )}
                      <td className="text-slate-500">{s.planName}</td>
                      <td className="text-slate-500">{s.startDate}</td>
                      <td>
                        <Badge value={s.state} />
                      </td>
                      <td className="font-medium">{money(s.total)}</td>
                      <td>
                        <Link
                          href={`/subscriptions/${s.id}`}
                          aria-label={`View ${s.number}`}
                        >
                          <ArrowUpRight className="size-4 text-slate-400" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={page}
              totalPages={result.data.totalPages}
              totalElements={result.data.totalElements}
              onChange={setPage}
            />
          </>
        )}
      </section>
    </>
  );
}
export function SubscriptionDetail({ id, user }: { id: string; user: User }) {
  const client = useQueryClient();
  const [pauseOpen, setPauseOpen] = useState(false);
  const [resumeOpen, setResumeOpen] = useState(false);
  const [renewOpen, setRenewOpen] = useState(false);
  const [closeOpen, setCloseOpen] = useState(false);
  const [renewPeriods, setRenewPeriods] = useState(1);
  const [renewExpiryDate, setRenewExpiryDate] = useState("");
  const [closeReason, setCloseReason] = useState("");

  const result = useQuery({
    queryKey: ["subscriptions", id],
    queryFn: ({ signal }) =>
      api<Subscription>(`/subscriptions/${id}`, { signal }),
  });
  const subInvoices = useQuery({
    queryKey: ["invoices", "subscription", id],
    queryFn: ({ signal }) =>
      api<Page<Invoice>>(
        `/invoices${queryString({ subscriptionId: Number(id), page: 0, size: 20 })}`,
        { signal },
      ),
  });
  const transition = useMutation({
    mutationFn: (state: string) =>
      api(`/subscriptions/${id}/transition`, {
        method: "POST",
        body: { state },
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      toast.success("Subscription moved to the next state.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const pauseMutation = useMutation({
    mutationFn: () =>
      api<Subscription>(`/subscriptions/${id}/pause`, {
        method: "POST",
        body: {},
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      setPauseOpen(false);
      toast.success("Subscription paused. Recurring billing is suspended.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const resumeMutation = useMutation({
    mutationFn: () =>
      api<Subscription>(`/subscriptions/${id}/resume`, {
        method: "POST",
        body: {},
      }),
    onSuccess: (updated) => {
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      setResumeOpen(false);
      toast.success(
        `Subscription resumed. Next billing date: ${updated.nextBillingDate}.`,
      );
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const renewMutation = useMutation({
    mutationFn: () =>
      api<Subscription>(`/subscriptions/${id}/renew`, {
        method: "POST",
        body: {
          newExpiryDate: renewExpiryDate || undefined,
          periods: renewExpiryDate ? undefined : renewPeriods,
        },
      }),
    onSuccess: (updated) => {
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      setRenewOpen(false);
      setRenewExpiryDate("");
      toast.success(
        `Subscription renewed through ${updated.expiryDate}.`,
      );
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const closeMutation = useMutation({
    mutationFn: () =>
      api<Subscription>(`/subscriptions/${id}/close`, {
        method: "POST",
        body: { reason: closeReason.trim() || undefined },
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      setCloseOpen(false);
      setCloseReason("");
      toast.success("Subscription closed early.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const billNow = useMutation({
    mutationFn: (billingDate: string) =>
      api<BillingRun>("/billing/run", {
        method: "POST",
        body: { billingDate, subscriptionId: Number(id) },
      }),
    onSuccess: (run) => {
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      client.invalidateQueries({ queryKey: ["invoices"] });
      client.invalidateQueries({ queryKey: ["billing-runs"] });
      toast.success(
        run.invoicesGenerated > 0
          ? `Generated ${run.invoicesGenerated} invoice for this subscription.`
          : "Subscription evaluated; no new due billing cycle on this date.",
      );
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const s = result.data;
  const index = states.indexOf(
    (s.state === "PAUSED" ? "ACTIVE" : s.state) as (typeof states)[number],
  );
  return (
    <>
      <Link
        href="/subscriptions"
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to subscriptions
      </Link>
      <Heading
        eyebrow="Subscription details"
        title={s.number}
        description={`${s.customerName} · ${s.planName}`}
        action={
          <div className="flex flex-wrap gap-2">
            {s.state === "DRAFT" && (
              <Button variant="outline" asChild>
                <Link href={`/subscriptions/${id}/edit`}>
                  <Pencil />
                  Edit draft
                </Link>
              </Button>
            )}
            {user.role === "ADMIN" && s.state === "ACTIVE" && (
              <Button
                variant="outline"
                disabled={billNow.isPending}
                onClick={() =>
                  billNow.mutate(
                    s.nextBillingDate || new Date().toISOString().slice(0, 10),
                  )
                }
              >
                {billNow.isPending ? "Billing…" : "Generate due invoice"}
              </Button>
            )}
            {user.role !== "CUSTOMER" && s.state === "ACTIVE" && (
              <Button
                variant="outline"
                disabled={pauseMutation.isPending}
                onClick={() => setPauseOpen(true)}
              >
                <Pause />
                Pause
              </Button>
            )}
            {user.role !== "CUSTOMER" && s.state === "PAUSED" && (
              <Button
                disabled={resumeMutation.isPending}
                onClick={() => setResumeOpen(true)}
              >
                <Play />
                Resume subscription
              </Button>
            )}
            {user.role !== "CUSTOMER" &&
              (s.state === "ACTIVE" || s.state === "CLOSED") && (
                <Button
                  variant="outline"
                  disabled={renewMutation.isPending}
                  onClick={() => setRenewOpen(true)}
                >
                  <RefreshCw />
                  Renew
                </Button>
              )}
            {user.role !== "CUSTOMER" &&
              (s.state === "ACTIVE" ||
                s.state === "PAUSED" ||
                s.state === "CONFIRMED") && (
                <Button
                  variant="outline"
                  disabled={closeMutation.isPending}
                  onClick={() => setCloseOpen(true)}
                >
                  <Ban />
                  Early close
                </Button>
              )}
            {user.role !== "CUSTOMER" &&
              s.state !== "PAUSED" &&
              index < states.length - 1 && (
                <Button
                  disabled={transition.isPending}
                  onClick={() => transition.mutate(states[index + 1])}
                >
                  {transition.isPending
                    ? "Updating…"
                    : `Move to ${human(states[index + 1])}`}
                  <ArrowRight />
                </Button>
              )}
          </div>
        }
      />
      <div className="card mb-6 flex w-full min-w-0 max-w-full gap-1 overflow-x-auto p-5">
        {states.map((state, i) => (
          <div className="flex min-w-fit flex-1 items-center gap-2" key={state}>
            <span
              className={`flex size-6 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold ${i <= index ? "bg-violet-600 text-white" : "bg-slate-100 text-slate-400"}`}
            >
              {i < index ? <Check size={12} /> : i + 1}
            </span>
            <span
              className={`text-xs ${i === index ? "font-semibold text-violet-700" : "text-slate-500"}`}
            >
              {state === "ACTIVE" && s.state === "PAUSED"
                ? "Active (Paused)"
                : human(state)}
            </span>
            {i < states.length - 1 && (
              <ChevronRight className="mx-auto size-4 text-slate-300" />
            )}
          </div>
        ))}
      </div>
      <section className="card mb-6 p-6">
        <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-semibold">Subscription overview</h2>
            <p className="mt-1 text-xs text-slate-400">
              Plan policies: Pausing{" "}
              {s.pausingAllowed ? "allowed" : "disabled"} · Renewal{" "}
              {s.renewalAllowed ? "allowed" : "disabled"} · Early closing{" "}
              {s.earlyClosingAllowed ? "allowed" : "disabled"}
            </p>
          </div>
          <Badge value={s.state} />
        </div>
        <dl className="grid gap-7 sm:grid-cols-3">
          <DetailValue label="Customer">{s.customerName}</DetailValue>
          <DetailValue label="Plan">
            {s.planName}
            {s.billingPeriod ? ` (${human(s.billingPeriod)})` : ""}
          </DetailValue>
          <DetailValue label="Payment terms">{s.paymentTerms}</DetailValue>
          <DetailValue label="Start date">{s.startDate}</DetailValue>
          <DetailValue label="Expiry date">{s.expiryDate}</DetailValue>
          <DetailValue label="Next billing date">
            {s.nextBillingDate || s.startDate}
          </DetailValue>
          <DetailValue label="Last billed date">
            {s.lastBilledDate || "Not billed yet"}
          </DetailValue>
          <DetailValue label="Line total">{money(s.total)}</DetailValue>
          <DetailValue label="Total paused days">
            {s.totalPausedDays ? `${s.totalPausedDays} days` : "0 days"}
          </DetailValue>
          {s.pausedBy && (
            <DetailValue label="Last paused">
              {s.pausedDate} by {s.pausedBy}
            </DetailValue>
          )}
          {s.resumedBy && (
            <DetailValue label="Last resumed">
              by {s.resumedBy}
            </DetailValue>
          )}
          {Boolean(s.renewalCount) && (
            <DetailValue label="Renewals">
              {s.renewalCount} time{s.renewalCount === 1 ? "" : "s"}
              {s.renewedBy ? ` (last by ${s.renewedBy})` : ""}
            </DetailValue>
          )}
          {s.closedBy && (
            <DetailValue label="Closed by">
              {s.closedBy}
              {s.closureReason ? ` · ${s.closureReason}` : ""}
            </DetailValue>
          )}
          {s.quotationId && (
            <DetailValue label="Source quotation">
              <Link
                href={`/quotations/${s.quotationId}`}
                className="text-violet-600 underline"
              >
                Quotation #{s.quotationId}
              </Link>
            </DetailValue>
          )}
        </dl>
      </section>

      <ConfirmDialog
        open={pauseOpen}
        onOpenChange={setPauseOpen}
        title={`Pause ${s.number}?`}
        confirmLabel="Pause subscription"
        pendingLabel="Pausing…"
        variant="default"
        pending={pauseMutation.isPending}
        onConfirm={() => pauseMutation.mutate()}
      >
        Pausing suspends recurring billing for this subscription until it is
        resumed.{!s.pausingAllowed && (
          <span className="mt-2 block font-semibold text-rose-600">
            Note: Plan "{s.planName}" does not allow pausing.
          </span>
        )}
      </ConfirmDialog>

      <ConfirmDialog
        open={resumeOpen}
        onOpenChange={setResumeOpen}
        title={`Resume ${s.number}?`}
        confirmLabel="Resume subscription"
        pendingLabel="Resuming…"
        variant="default"
        pending={resumeMutation.isPending}
        onConfirm={() => resumeMutation.mutate()}
      >
        Resuming restores this subscription to ACTIVE and shifts its next
        billing and expiry dates forward by any paused days so the customer is
        not charged for paused time.
      </ConfirmDialog>

      <ConfirmDialog
        open={renewOpen}
        onOpenChange={setRenewOpen}
        title={`Renew ${s.number}`}
        confirmLabel="Renew subscription"
        pendingLabel="Renewing…"
        variant="default"
        pending={renewMutation.isPending}
        onConfirm={() => renewMutation.mutate()}
      >
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            Current expiry date:{" "}
            <span className="font-semibold text-slate-800">
              {s.expiryDate}
            </span>
            . Prior invoices and billing history are preserved.
          </p>
          {!s.renewalAllowed && (
            <p className="text-xs font-semibold text-rose-600">
              Note: Plan "{s.planName}" does not allow renewal.
            </p>
          )}
          <FormField
            label="Extend by billing cycles"
            hint="Used when a specific new expiry date is not entered."
          >
            <input
              type="number"
              min="1"
              max="60"
              className="input"
              value={renewPeriods}
              onChange={(e) => setRenewPeriods(Number(e.target.value) || 1)}
            />
          </FormField>
          <FormField label="Or choose explicit new expiry date (optional)">
            <input
              type="date"
              className="input"
              value={renewExpiryDate}
              onChange={(e) => setRenewExpiryDate(e.target.value)}
            />
          </FormField>
        </div>
      </ConfirmDialog>

      <ConfirmDialog
        open={closeOpen}
        onOpenChange={setCloseOpen}
        title={`Close ${s.number} early?`}
        confirmLabel="Close subscription"
        pendingLabel="Closing…"
        variant="destructive"
        pending={closeMutation.isPending}
        onConfirm={() => closeMutation.mutate()}
      >
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            Early closing stops all future recurring billing while preserving
            historical invoices.
          </p>
          {!s.earlyClosingAllowed && (
            <p className="text-xs font-semibold text-rose-600">
              Note: Plan "{s.planName}" does not allow early closing before{" "}
              {s.expiryDate}.
            </p>
          )}
          <FormField label="Closure reason">
            <input
              className="input"
              placeholder="Customer requested early termination…"
              value={closeReason}
              onChange={(e) => setCloseReason(e.target.value)}
            />
          </FormField>
        </div>
      </ConfirmDialog>
      <section className="card overflow-hidden">
        <div className="border-b border-slate-100 p-5">
          <h2 className="font-semibold">Subscription lines</h2>
          <p className="mt-1 text-xs text-slate-400">
            Prices and taxes are stored with this subscription.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Variant</th>
                <th>Quantity</th>
                <th>Unit price</th>
                <th>Tax rate</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {s.lines.map((line) => (
                <tr key={line.id}>
                  <td className="font-medium">{line.productName}</td>
                  <td className="text-slate-500">
                    {line.variantName || "Base product"}
                  </td>
                  <td>{line.quantity}</td>
                  <td>{money(line.unitPrice)}</td>
                  <td>{line.taxRate}%</td>
                  <td className="font-medium">{money(line.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="flex justify-end gap-8 border-t border-slate-100 bg-slate-50/50 p-6">
          <span className="text-sm text-slate-500">Subscription total</span>
          <span className="text-lg font-semibold">{money(s.total)}</span>
        </div>
      </section>
      <section className="card mt-6 overflow-hidden">
        <div className="flex items-center justify-between border-b border-slate-100 p-5">
          <div>
            <h2 className="font-semibold">Generated invoices</h2>
            <p className="mt-1 text-xs text-slate-400">
              Invoices produced from recurring billing cycles for this
              subscription.
            </p>
          </div>
          <Link
            href="/invoices"
            className="text-xs font-medium text-violet-600"
          >
            All invoices
          </Link>
        </div>
        {subInvoices.isPending ? (
          <div className="p-5">
            <Loading />
          </div>
        ) : subInvoices.error ? (
          <div className="p-5">
            <ErrorState error={subInvoices.error} />
          </div>
        ) : !subInvoices.data.content.length ? (
          <Empty
            title="No invoices generated yet"
            description="Invoices are created when this subscription is active and recurring billing executes."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>Invoice</th>
                  <th>Billing period</th>
                  <th>Issue date</th>
                  <th>State</th>
                  <th>Total</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {subInvoices.data.content.map((inv) => (
                  <tr key={inv.id}>
                    <td>
                      <Link href={`/invoices/${inv.id}`}>
                        {inv.invoiceNumber}
                      </Link>
                    </td>
                    <td className="text-slate-500">
                      {inv.billingPeriodStart} → {inv.billingPeriodEnd}
                    </td>
                    <td className="text-slate-500">{inv.issueDate}</td>
                    <td>
                      <Badge value={inv.state} />
                    </td>
                    <td className="font-medium">{money(inv.total)}</td>
                    <td>
                      <Link
                        href={`/invoices/${inv.id}`}
                        aria-label={`View ${inv.invoiceNumber}`}
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
  );
}
export function SubscriptionEditor({ id, user }: { id?: string; user: User }) {
  const result = useQuery({
    queryKey: ["subscriptions", id],
    queryFn: ({ signal }) =>
      api<Subscription>(`/subscriptions/${id}`, { signal }),
    enabled: !!id,
  });
  if (id && result.isPending) return <Loading />;
  if (result.error) return <ErrorState error={result.error} />;
  if (result.data && result.data.state !== "DRAFT")
    return (
      <ErrorState
        error={new Error("Only draft subscriptions can be edited.")}
      />
    );
  return (
    <SubscriptionForm key={id || "new"} initial={result.data} user={user} />
  );
}
function RecordPicker<T extends { id: number; name: string }>({
  label,
  endpoint,
  value,
  onChange,
  error,
  queryKey,
}: {
  label: string;
  endpoint: string;
  value?: number;
  onChange: (value: number) => void;
  error?: string;
  queryKey: string;
}) {
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: [queryKey, "picker", search, page],
    queryFn: ({ signal }) =>
      api<Page<T>>(
        `${endpoint}${queryString({ q: search, page, size: 20, status: "ACTIVE" })}`,
        { signal },
      ),
  });
  return (
    <div className="rounded-xl border border-slate-200 p-4">
      <FormField label={label} error={error}>
        <select
          className="input"
          value={value || ""}
          onChange={(e) => onChange(Number(e.target.value))}
        >
          <option value="">Select {label.toLowerCase()}</option>
          {Boolean(value) &&
            !result.data?.content.some((v) => v.id === value) && (
              <option value={value}>Selected #{value}</option>
            )}
          {result.data?.content.map((v) => (
            <option key={v.id} value={v.id}>
              {v.name}
            </option>
          ))}
        </select>
      </FormField>
      <div className="mt-3">
        <input
          className="input !min-h-8 !py-2 text-xs"
          value={search}
          aria-label={`Search ${label}`}
          placeholder={`Search ${label.toLowerCase()}…`}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
      </div>
      {result.isPending ? (
        <p className="mt-2 text-xs text-slate-400">Loading choices…</p>
      ) : result.error ? (
        <p role="alert" className="mt-2 text-xs text-rose-600">
          {errorMessage(result.error)}
        </p>
      ) : (
        <div className="mt-2 flex items-center justify-between text-[10px] text-slate-400">
          <span>
            {result.data.totalElements} available · Page {page + 1}
          </span>
          <div className="flex gap-3">
            <button
              type="button"
              disabled={page === 0}
              className="disabled:opacity-30"
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </button>
            <button
              type="button"
              disabled={page + 1 >= result.data.totalPages}
              className="disabled:opacity-30"
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
function SubscriptionForm({
  initial,
  user,
}: {
  initial?: Subscription;
  user: User;
}) {
  const router = useRouter();
  const client = useQueryClient();
  const form = useForm<z.infer<typeof subscriptionSchema>>({
    resolver: zodResolver(subscriptionSchema),
    defaultValues: initial
      ? {
          customerId: initial.customerId,
          planId: initial.planId,
          startDate: initial.startDate,
          expiryDate: initial.expiryDate,
          paymentTerms: initial.paymentTerms,
          lines: initial.lines.map((line) => ({
            productId: line.productId,
            variantId: line.variantId || undefined,
            quantity: Number(line.quantity),
            taxRate: Number(line.taxRate),
          })),
        }
      : {
          customerId: undefined,
          planId: 0,
          startDate: new Date().toISOString().slice(0, 10),
          expiryDate: "",
          paymentTerms: "Due on receipt",
          lines: [{ productId: 0, quantity: 1, taxRate: 0 }],
        },
  });
  const lines = useFieldArray({ control: form.control, name: "lines" });
  const planId = form.watch("planId");
  const customerId = form.watch("customerId");
  const plan = useQuery({
    queryKey: ["plans", String(planId)],
    queryFn: ({ signal }) => api<Plan>(`/plans/${planId}`, { signal }),
    enabled: !!planId,
  });
  const e = form.formState.errors;
  async function save(values: z.infer<typeof subscriptionSchema>) {
    if (user.role !== "CUSTOMER" && !values.customerId) {
      form.setError("customerId", { message: "Choose a customer." });
      return;
    }
    try {
      const body =
        user.role === "CUSTOMER"
          ? { ...values, customerId: undefined }
          : values;
      const saved = await api<Subscription>(
        initial ? `/subscriptions/${initial.id}` : "/subscriptions",
        { method: initial ? "PUT" : "POST", body },
      );
      await client.invalidateQueries({ queryKey: ["subscriptions"] });
      toast.success("Draft subscription saved.");
      router.push(`/subscriptions/${saved.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }
  return (
    <>
      <Heading
        eyebrow="Business in motion"
        title={initial ? "Edit draft subscription" : "Create a subscription"}
        description="Start with a draft. Choose a plan and add the products your customer needs."
      />
      <form className="max-w-5xl" onSubmit={form.handleSubmit(save)} noValidate>
        <section className="card p-6">
          <h2 className="mb-6 font-semibold">Subscription information</h2>
          <div className="grid gap-5 sm:grid-cols-2">
            {user.role !== "CUSTOMER" && (
              <RecordPicker<Customer>
                label="Customer"
                endpoint="/customers"
                queryKey="customers"
                value={customerId}
                onChange={(v) =>
                  form.setValue("customerId", v, { shouldValidate: true })
                }
                error={e.customerId?.message}
              />
            )}
            <RecordPicker<Plan>
              label="Plan"
              endpoint="/plans"
              queryKey="plans"
              value={planId}
              onChange={(v) => {
                form.setValue("planId", v, { shouldValidate: true });
                form.getValues("lines").forEach((_, index) => {
                  form.setValue(`lines.${index}.productId`, 0);
                  form.setValue(`lines.${index}.variantId`, undefined);
                });
              }}
              error={e.planId?.message}
            />
            <FormField label="Start date" error={e.startDate?.message}>
              <input
                className="input"
                type="date"
                {...form.register("startDate")}
              />
            </FormField>
            <FormField label="Expiry date" error={e.expiryDate?.message}>
              <input
                className="input"
                type="date"
                {...form.register("expiryDate")}
              />
            </FormField>
            <div className="sm:col-span-2">
              <FormField label="Payment terms" error={e.paymentTerms?.message}>
                <input className="input" {...form.register("paymentTerms")} />
              </FormField>
            </div>
          </div>
          {plan.data && (
            <div className="mt-5 rounded-lg bg-violet-50 p-4 text-xs leading-6 text-violet-700">
              {plan.data.name} · {human(plan.data.billingPeriod)} · Minimum
              quantity {plan.data.minimumQuantity} · Available{" "}
              {plan.data.availableFrom} to {plan.data.availableUntil}
            </div>
          )}
        </section>
        <section className="card mt-6 p-6">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h2 className="font-semibold">Subscription lines</h2>
              <p className="mt-1 text-xs text-slate-400">
                Final prices and totals are calculated by the server.
              </p>
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() =>
                lines.append({ productId: 0, quantity: 1, taxRate: 0 })
              }
            >
              <Plus />
              Add line
            </Button>
          </div>
          {lines.fields.map((line, i) => (
            <SubscriptionLine
              key={line.id}
              index={i}
              form={form}
              productIds={plan.data?.productIds}
              onRemove={() => lines.remove(i)}
              canRemove={lines.fields.length > 1}
            />
          ))}
          {e.lines?.message && (
            <p role="alert" className="text-xs text-rose-600">
              {e.lines.message}
            </p>
          )}
          <FormActions
            pending={form.formState.isSubmitting}
            cancel={initial ? `/subscriptions/${initial.id}` : "/subscriptions"}
            label="Save draft"
          />
        </section>
      </form>
    </>
  );
}
function SubscriptionLine({
  index,
  form,
  productIds,
  onRemove,
  canRemove,
}: {
  index: number;
  form: ReturnType<typeof useForm<z.infer<typeof subscriptionSchema>>>;
  productIds?: number[];
  onRemove: () => void;
  canRemove: boolean;
}) {
  const productId = form.watch(`lines.${index}.productId`);
  const variantId = form.watch(`lines.${index}.variantId`);
  const product = useQuery({
    queryKey: ["products", String(productId)],
    queryFn: ({ signal }) => api<Product>(`/products/${productId}`, { signal }),
    enabled: !!productId,
  });
  const lineError = form.formState.errors.lines?.[index];
  return (
    <div className="mb-4 rounded-xl border border-slate-200 bg-slate-50/60 p-4">
      <div className="mb-4 flex items-center justify-between">
        <span className="text-xs font-semibold text-slate-500">
          LINE {index + 1}
        </span>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          aria-label={`Remove line ${index + 1}`}
          disabled={!canRemove}
          onClick={onRemove}
        >
          <Trash2 />
          Remove
        </Button>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <AssociatedProductPicker
          productIds={productIds}
          value={productId}
          onChange={(v) => {
            form.setValue(`lines.${index}.productId`, v, {
              shouldValidate: true,
            });
            form.setValue(`lines.${index}.variantId`, undefined);
          }}
          error={lineError?.productId?.message}
        />
        <div className="space-y-4">
          <FormField label="Variant">
            <select
              className="input"
              value={variantId || ""}
              disabled={!product.data}
              onChange={(e) =>
                form.setValue(
                  `lines.${index}.variantId`,
                  e.target.value ? Number(e.target.value) : undefined,
                )
              }
            >
              <option value="">Base product</option>
              {product.data?.variants.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name} ({adjustment(v.priceAdjustment)})
                </option>
              ))}
            </select>
          </FormField>
          {product.data && (
            <p className="text-xs text-slate-400">
              Base sales price {money(product.data.salesPrice)}
              {productIds && !productIds.includes(productId) && (
                <span className="mt-1 block text-amber-600">
                  This product is not associated with the selected plan. Choose
                  an associated product.
                </span>
              )}
            </p>
          )}
        </div>
        <FormField label="Quantity" error={lineError?.quantity?.message}>
          <input
            className="input"
            type="number"
            min="1"
            step="1"
            {...form.register(`lines.${index}.quantity`, {
              valueAsNumber: true,
            })}
          />
        </FormField>
        <FormField label="Tax rate (%)" error={lineError?.taxRate?.message}>
          <input
            className="input"
            type="number"
            min="0"
            max="100"
            step="0.01"
            {...form.register(`lines.${index}.taxRate`, {
              valueAsNumber: true,
            })}
          />
        </FormField>
      </div>
    </div>
  );
}

export function AssociatedProductPicker({
  productIds,
  value,
  onChange,
  error,
}: {
  productIds?: number[];
  value: number;
  onChange: (value: number) => void;
  error?: string;
}) {
  const products = useQueries({
    queries: (productIds || []).map((id) => ({
      queryKey: ["products", String(id)],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        api<Product>(`/products/${id}`, { signal }),
    })),
  });
  const loading = products.some((product) => product.isPending);
  const failed = products.find((product) => product.error)?.error;
  const choices = products.flatMap((product) =>
    product.data?.status === "ACTIVE" ? [product.data] : [],
  );
  return (
    <div className="rounded-xl border border-slate-200 p-4">
      <FormField
        label="Product"
        error={error}
        hint="Only active products associated with the selected plan are available."
      >
        <select
          className="input"
          value={value || ""}
          disabled={!productIds || loading}
          onChange={(event) => onChange(Number(event.target.value))}
        >
          <option value="">
            {!productIds
              ? "Choose a plan first"
              : loading
                ? "Loading associated products…"
                : "Select product"}
          </option>
          {value && !choices.some((product) => product.id === value) ? (
            <option value={value}>Unavailable product #{value}</option>
          ) : null}
          {choices.map((product) => (
            <option key={product.id} value={product.id}>
              {product.name} · {money(product.salesPrice)}
            </option>
          ))}
        </select>
      </FormField>
      {failed ? (
        <p role="alert" className="mt-2 text-xs text-rose-600">
          {errorMessage(failed)}
        </p>
      ) : productIds && !loading && !choices.length ? (
        <p className="mt-2 text-xs text-slate-500">
          No active products are associated with this plan.
        </p>
      ) : null}
    </div>
  );
}
