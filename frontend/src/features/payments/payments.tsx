"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  AlertTriangle,
  ArrowLeft,
  ArrowUpRight,
  Clock,
  CreditCard,
  RotateCcw,
  Wallet,
} from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage, queryString } from "@/lib/api";
import { human, money } from "@/lib/utils";
import {
  paymentMethods,
  paymentStatuses,
  type Invoice,
  type Page,
  type Payment,
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

const paymentSchema = z.object({
  invoiceId: z.number().min(1, "Select a confirmed invoice."),
  method: z.enum(["BANK_TRANSFER", "CASH", "UPI", "CARD", "OTHER"]),
  amount: z.number().positive("Payment amount must be greater than zero."),
  paymentDate: z.string().min(1, "Payment date is required."),
  paymentReference: z.string().max(100).optional(),
  note: z.string().max(1000).optional(),
});

export function PaymentList({ user }: { user: User }) {
  const [tab, setTab] = useState<"payments" | "overdue">("payments");
  const [q, setQ] = useState("");
  const [method, setMethod] = useState("");
  const [status, setStatus] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(0);

  const [invFilter, setInvFilter] = useState<"overdue" | "outstanding">(
    "overdue",
  );
  const [invPage, setInvPage] = useState(0);
  const [followupInvoice, setFollowupInvoice] = useState<Invoice | null>(null);
  const [followupNotes, setFollowupNotes] = useState("");
  const client = useQueryClient();

  const paymentsQuery = useQuery({
    queryKey: ["payments", { q, method, status, fromDate, toDate, page }],
    queryFn: ({ signal }) =>
      api<Page<Payment>>(
        `/payments${queryString({
          q,
          method: method || undefined,
          status: status || undefined,
          fromDate: fromDate || undefined,
          toDate: toDate || undefined,
          page,
          size: 10,
        })}`,
        { signal },
      ),
  });

  const outstandingInvoicesQuery = useQuery({
    queryKey: ["invoices", "outstanding-summary"],
    queryFn: ({ signal }) =>
      api<Page<Invoice>>(
        `/invoices${queryString({ outstanding: true, page: 0, size: 50 })}`,
        { signal },
      ),
  });

  const trackedInvoicesQuery = useQuery({
    queryKey: ["invoices", "collections", invFilter, invPage, q],
    queryFn: ({ signal }) =>
      api<Page<Invoice>>(
        `/invoices${queryString({
          q,
          overdue: invFilter === "overdue" ? true : undefined,
          outstanding: invFilter === "outstanding" ? true : undefined,
          page: invPage,
          size: 10,
        })}`,
        { signal },
      ),
  });

  const followUpMutation = useMutation({
    mutationFn: ({ id, notes }: { id: number; notes: string }) =>
      api<Invoice>(`/invoices/${id}/follow-up`, {
        method: "POST",
        body: { notes },
      }),
    onSuccess: (updated) => {
      client.invalidateQueries({ queryKey: ["invoices"] });
      setFollowupInvoice(null);
      setFollowupNotes("");
      toast.success(`Follow-up recorded for ${updated.invoiceNumber}.`);
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const outstandingList = outstandingInvoicesQuery.data?.content || [];
  const totalOutstanding = outstandingList.reduce(
    (acc, inv) => acc + Number(inv.outstandingAmount || 0),
    0,
  );
  const overdueCount = outstandingList.filter((inv) => inv.overdue).length;
  const overdueBalance = outstandingList
    .filter((inv) => inv.overdue)
    .reduce((acc, inv) => acc + Number(inv.outstandingAmount || 0), 0);

  return (
    <>
      <Heading
        eyebrow={
          user.role === "CUSTOMER"
            ? "Your payment history"
            : "Receivables & reconciliation"
        }
        title="Payments"
        description={
          user.role === "CUSTOMER"
            ? "Track recorded payments and remaining balances across your invoices."
            : "Record full or partial payments, reconcile invoice balances, and follow up on overdue receivables."
        }
        action={
          user.role !== "CUSTOMER" ? (
            <NewButton href="/payments/new">Record payment</NewButton>
          ) : undefined
        }
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <div className="card p-5">
          <div className="flex items-center justify-between text-xs font-medium text-slate-400">
            <span>Total outstanding balance</span>
            <Wallet className="size-4 text-violet-500" />
          </div>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {money(totalOutstanding)}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            Across {outstandingList.length} unpaid or partially paid invoice
            {outstandingList.length === 1 ? "" : "s"}
          </p>
        </div>
        <div className="card p-5">
          <div className="flex items-center justify-between text-xs font-medium text-slate-400">
            <span>Overdue balance</span>
            <AlertTriangle className="size-4 text-amber-500" />
          </div>
          <p className="mt-2 text-2xl font-semibold text-amber-600">
            {money(overdueBalance)}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            {overdueCount} overdue invoice{overdueCount === 1 ? "" : "s"} past
            due date
          </p>
        </div>
        <div className="card p-5">
          <div className="flex items-center justify-between text-xs font-medium text-slate-400">
            <span>Recorded payments</span>
            <CreditCard className="size-4 text-emerald-500" />
          </div>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {paymentsQuery.data?.totalElements ?? 0}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            Reconciled against confirmed customer invoices
          </p>
        </div>
      </div>

      <div className="mb-5 flex gap-2">
        <Button
          type="button"
          variant={tab === "payments" ? "default" : "outline"}
          size="sm"
          onClick={() => setTab("payments")}
        >
          Recorded payments
        </Button>
        <Button
          type="button"
          variant={tab === "overdue" ? "default" : "outline"}
          size="sm"
          onClick={() => setTab("overdue")}
        >
          Outstanding & overdue invoices ({outstandingList.length})
        </Button>
      </div>

      {tab === "payments" ? (
        <section className="card overflow-hidden">
          <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-5">
            <SearchField
              value={q}
              onChange={(v) => {
                setQ(v);
                setPage(0);
              }}
              placeholder="Search reference, invoice, customer…"
            />
            <select
              aria-label="Filter payment method"
              className="input sm:!w-44"
              value={method}
              onChange={(e) => {
                setMethod(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All methods</option>
              {paymentMethods.map((m) => (
                <option key={m} value={m}>
                  {human(m)}
                </option>
              ))}
            </select>
            <select
              aria-label="Filter payment status"
              className="input sm:!w-40"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              {paymentStatuses.map((s) => (
                <option key={s} value={s}>
                  {human(s)}
                </option>
              ))}
            </select>
            <input
              type="date"
              aria-label="From payment date"
              className="input sm:!w-38"
              value={fromDate}
              onChange={(e) => {
                setFromDate(e.target.value);
                setPage(0);
              }}
            />
            <input
              type="date"
              aria-label="To payment date"
              className="input sm:!w-38"
              value={toDate}
              onChange={(e) => {
                setToDate(e.target.value);
                setPage(0);
              }}
            />
          </div>

          {paymentsQuery.isPending ? (
            <div className="p-5">
              <Loading />
            </div>
          ) : paymentsQuery.error ? (
            <div className="p-5">
              <ErrorState
                error={paymentsQuery.error}
                retry={() => paymentsQuery.refetch()}
              />
            </div>
          ) : !paymentsQuery.data.content.length ? (
            <Empty
              title="No payments recorded yet"
              description="Record a payment against a confirmed invoice to reconcile customer balances."
              action={
                user.role !== "CUSTOMER" ? (
                  <NewButton href="/payments/new">Record payment</NewButton>
                ) : undefined
              }
            />
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Reference</th>
                      <th>Invoice</th>
                      {user.role !== "CUSTOMER" && <th>Customer</th>}
                      <th>Method</th>
                      <th>Date</th>
                      <th>Status</th>
                      <th>Amount</th>
                      <th>Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paymentsQuery.data.content.map((p) => (
                      <tr key={p.id}>
                        <td>
                          <Link href={`/payments/${p.id}`}>
                            {p.paymentReference}
                          </Link>
                        </td>
                        <td>
                          <Link
                            href={`/invoices/${p.invoiceId}`}
                            className="text-xs font-medium text-violet-600"
                          >
                            {p.invoiceNumber}
                          </Link>
                        </td>
                        {user.role !== "CUSTOMER" && (
                          <td className="text-slate-500">{p.customerName}</td>
                        )}
                        <td className="text-slate-500">{human(p.method)}</td>
                        <td className="text-slate-500">{p.paymentDate}</td>
                        <td>
                          <Badge value={p.status} />
                        </td>
                        <td className="font-medium">{money(p.amount)}</td>
                        <td>
                          <Link
                            href={`/payments/${p.id}`}
                            aria-label={`View ${p.paymentReference}`}
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
                totalPages={paymentsQuery.data.totalPages}
                totalElements={paymentsQuery.data.totalElements}
                onChange={setPage}
              />
            </>
          )}
        </section>
      ) : (
        <section className="card overflow-hidden">
          <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-5">
            <SearchField
              value={q}
              onChange={(v) => {
                setQ(v);
                setInvPage(0);
              }}
              placeholder="Search overdue or outstanding invoices…"
            />
            <select
              aria-label="Filter receivable status"
              className="input sm:ml-auto sm:!w-52"
              value={invFilter}
              onChange={(e) => {
                setInvFilter(e.target.value as "overdue" | "outstanding");
                setInvPage(0);
              }}
            >
              <option value="overdue">Overdue invoices only</option>
              <option value="outstanding">All outstanding invoices</option>
            </select>
          </div>
          {trackedInvoicesQuery.isPending ? (
            <div className="p-5">
              <Loading />
            </div>
          ) : trackedInvoicesQuery.error ? (
            <div className="p-5">
              <ErrorState
                error={trackedInvoicesQuery.error}
                retry={() => trackedInvoicesQuery.refetch()}
              />
            </div>
          ) : !trackedInvoicesQuery.data.content.length ? (
            <Empty
              title={
                invFilter === "overdue"
                  ? "No overdue invoices"
                  : "No outstanding invoices"
              }
              description="All confirmed invoices are settled or still within their payment terms."
            />
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Invoice</th>
                      <th>Customer</th>
                      <th>Due date</th>
                      <th>Total</th>
                      <th>Paid</th>
                      <th>Outstanding</th>
                      <th>Overdue status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {trackedInvoicesQuery.data.content.map((inv) => (
                      <tr key={inv.id}>
                        <td>
                          <Link href={`/invoices/${inv.id}`}>
                            {inv.invoiceNumber}
                          </Link>
                        </td>
                        <td className="text-slate-500">{inv.customerName}</td>
                        <td className="text-slate-500">{inv.dueDate}</td>
                        <td>{money(inv.total)}</td>
                        <td className="text-slate-500">
                          {money(inv.amountPaid || 0)}
                        </td>
                        <td className="font-semibold text-slate-900">
                          {money(inv.outstandingAmount ?? inv.total)}
                        </td>
                        <td>
                          {inv.overdue ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700">
                              <Clock className="size-3" />
                              {inv.daysOverdue}d overdue
                            </span>
                          ) : (
                            <Badge value={inv.paymentStatus || "UNPAID"} />
                          )}
                        </td>
                        <td>
                          <div className="flex flex-wrap gap-2">
                            {user.role !== "CUSTOMER" && (
                              <>
                                <Button size="sm" asChild>
                                  <Link
                                    href={`/payments/new?invoiceId=${inv.id}`}
                                  >
                                    Record payment
                                  </Link>
                                </Button>
                                <Button
                                  size="sm"
                                  variant="outline"
                                  onClick={() => {
                                    setFollowupInvoice(inv);
                                    setFollowupNotes(inv.followupNotes || "");
                                  }}
                                >
                                  Follow up
                                </Button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={invPage}
                totalPages={trackedInvoicesQuery.data.totalPages}
                totalElements={trackedInvoicesQuery.data.totalElements}
                onChange={setInvPage}
              />
            </>
          )}
        </section>
      )}

      <ConfirmDialog
        open={!!followupInvoice}
        onOpenChange={(open) => {
          if (!open) setFollowupInvoice(null);
        }}
        title={`Log follow-up for ${followupInvoice?.invoiceNumber || ""}`}
        confirmLabel="Save follow-up"
        pendingLabel="Saving…"
        variant="default"
        pending={followUpMutation.isPending}
        onConfirm={() => {
          if (!followupInvoice || !followupNotes.trim()) {
            toast.error("Enter follow-up notes.");
            return;
          }
          followUpMutation.mutate({
            id: followupInvoice.id,
            notes: followupNotes.trim(),
          });
        }}
      >
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            Record reminder or collection notes for{" "}
            <span className="font-semibold text-slate-700">
              {followupInvoice?.customerName}
            </span>{" "}
            (Outstanding:{" "}
            {money(followupInvoice?.outstandingAmount ?? followupInvoice?.total ?? 0)}
            ).
          </p>
          <textarea
            aria-label="Follow-up notes"
            className="input min-h-24 w-full"
            placeholder="Spoke with customer accounting team; promised UPI transfer by Friday…"
            value={followupNotes}
            onChange={(e) => setFollowupNotes(e.target.value)}
          />
        </div>
      </ConfirmDialog>
    </>
  );
}

export function PaymentDetail({ id, user }: { id: string; user: User }) {
  const [reverseOpen, setReverseOpen] = useState(false);
  const [reversalReason, setReversalReason] = useState("");
  const [reversalReference, setReversalReference] = useState("");
  const client = useQueryClient();

  const result = useQuery({
    queryKey: ["payments", id],
    queryFn: ({ signal }) => api<Payment>(`/payments/${id}`, { signal }),
  });

  const reverseMutation = useMutation({
    mutationFn: () =>
      api<Payment>(`/payments/${id}/reverse`, {
        method: "POST",
        body: {
          reason: reversalReason.trim(),
          reversalReference: reversalReference.trim() || undefined,
        },
      }),
    onSuccess: (updated) => {
      client.invalidateQueries({ queryKey: ["payments"] });
      client.invalidateQueries({ queryKey: ["invoices"] });
      setReverseOpen(false);
      toast.success(`Payment ${updated.paymentReference} reversed.`);
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const p = result.data;

  return (
    <>
      <Link
        href="/payments"
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to payments
      </Link>
      <Heading
        eyebrow="Payment record"
        title={p.paymentReference}
        description={`${p.customerName} · Invoice ${p.invoiceNumber}`}
        action={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" asChild>
              <Link href={`/invoices/${p.invoiceId}`}>View invoice</Link>
            </Button>
            {user.role !== "CUSTOMER" && p.status === "COMPLETED" && (
              <Button
                variant="destructive"
                onClick={() => setReverseOpen(true)}
              >
                <RotateCcw />
                Reverse payment
              </Button>
            )}
          </div>
        }
      />

      <section className="card mb-6 p-6">
        <div className="mb-7 flex items-center justify-between">
          <div>
            <h2 className="font-semibold">Payment details</h2>
            <p className="mt-1 text-xs text-slate-400">
              Recorded against{" "}
              <Link
                href={`/invoices/${p.invoiceId}`}
                className="font-semibold text-violet-600"
              >
                {p.invoiceNumber}
              </Link>
            </p>
          </div>
          <Badge value={p.status} />
        </div>
        <dl className="grid gap-7 sm:grid-cols-3">
          <DetailValue label="Payment reference">
            {p.paymentReference}
          </DetailValue>
          <DetailValue label="Invoice">
            <Link
              href={`/invoices/${p.invoiceId}`}
              className="text-violet-600 underline"
            >
              {p.invoiceNumber}
            </Link>
          </DetailValue>
          <DetailValue label="Customer">{p.customerName}</DetailValue>
          <DetailValue label="Payment method">{human(p.method)}</DetailValue>
          <DetailValue label="Amount">{money(p.amount)}</DetailValue>
          <DetailValue label="Payment date">{p.paymentDate}</DetailValue>
          <DetailValue label="Recorded by">{p.recordedBy}</DetailValue>
          <DetailValue label="Idempotency key">{p.idempotencyKey}</DetailValue>
          <DetailValue label="Note">{p.note || "—"}</DetailValue>
          {p.invoiceTotal !== undefined && (
            <>
              <DetailValue label="Invoice total">
                {money(p.invoiceTotal)}
              </DetailValue>
              <DetailValue label="Invoice total paid">
                {money(p.invoiceAmountPaid || 0)}
              </DetailValue>
              <DetailValue label="Invoice remaining balance">
                {money(p.invoiceOutstandingAmount || 0)}
              </DetailValue>
            </>
          )}
          {p.status === "REVERSED" && (
            <>
              <DetailValue label="Reversed by">{p.reversedBy}</DetailValue>
              <DetailValue label="Reversal reference">
                {p.reversalReference}
              </DetailValue>
              <DetailValue label="Reversal reason">
                {p.reversalReason}
              </DetailValue>
            </>
          )}
        </dl>
      </section>

      <ConfirmDialog
        open={reverseOpen}
        onOpenChange={setReverseOpen}
        title={`Reverse payment ${p.paymentReference}?`}
        confirmLabel="Confirm reversal"
        pendingLabel="Reversing…"
        variant="destructive"
        pending={reverseMutation.isPending}
        onConfirm={() => {
          if (!reversalReason.trim()) {
            toast.error("Please provide a reason for reversing this payment.");
            return;
          }
          reverseMutation.mutate();
        }}
      >
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            Reversing this payment preserves the audit history and restores{" "}
            <span className="font-semibold text-slate-700">
              {money(p.amount)}
            </span>{" "}
            to the outstanding balance of invoice {p.invoiceNumber}.
          </p>
          <FormField label="Reversal reason">
            <input
              className="input"
              placeholder="Duplicate entry / bank return…"
              value={reversalReason}
              onChange={(e) => setReversalReason(e.target.value)}
            />
          </FormField>
          <FormField label="Reversal reference (optional)">
            <input
              className="input"
              placeholder="REV-..."
              value={reversalReference}
              onChange={(e) => setReversalReference(e.target.value)}
            />
          </FormField>
        </div>
      </ConfirmDialog>
    </>
  );
}

export function RecordPaymentEditor({
  defaultInvoiceId,
}: {
  defaultInvoiceId?: string;
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const client = useQueryClient();
  const preselectedId =
    defaultInvoiceId || searchParams.get("invoiceId") || "";

  const payableInvoices = useQuery({
    queryKey: ["invoices", "payable-picker"],
    queryFn: ({ signal }) =>
      api<Page<Invoice>>(
        `/invoices${queryString({ outstanding: true, page: 0, size: 100 })}`,
        { signal },
      ),
  });

  const form = useForm<z.infer<typeof paymentSchema>>({
    resolver: zodResolver(paymentSchema),
    defaultValues: {
      invoiceId: preselectedId ? Number(preselectedId) : 0,
      method: "BANK_TRANSFER",
      amount: 0,
      paymentDate: new Date().toISOString().slice(0, 10),
      paymentReference: "",
      note: "",
    },
  });

  const selectedInvoiceId = form.watch("invoiceId");
  const selectedInvoiceQuery = useQuery({
    queryKey: ["invoices", String(selectedInvoiceId)],
    queryFn: ({ signal }) =>
      api<Invoice>(`/invoices/${selectedInvoiceId}`, { signal }),
    enabled: !!selectedInvoiceId && selectedInvoiceId > 0,
  });

  const selectedInvoice = selectedInvoiceQuery.data;
  const e = form.formState.errors;

  async function submit(values: z.infer<typeof paymentSchema>) {
    try {
      const saved = await api<Payment>("/payments", {
        method: "POST",
        body: {
          invoiceId: values.invoiceId,
          method: values.method,
          amount: values.amount.toFixed(2),
          paymentDate: values.paymentDate,
          paymentReference: values.paymentReference?.trim() || undefined,
          note: values.note?.trim() || undefined,
        },
      });
      await client.invalidateQueries({ queryKey: ["payments"] });
      await client.invalidateQueries({ queryKey: ["invoices"] });
      toast.success(
        saved.invoiceState === "PAID"
          ? `Payment recorded. Invoice ${saved.invoiceNumber} is now PAID!`
          : `Partial payment of ${money(saved.amount)} recorded.`,
      );
      router.push(`/invoices/${saved.invoiceId}`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  return (
    <>
      <Heading
        eyebrow="Payment reconciliation"
        title="Record a payment"
        description="Record a full or partial payment against a confirmed invoice. When total payments match the invoice total, the invoice automatically transitions to Paid."
      />
      <form
        className="max-w-3xl"
        onSubmit={form.handleSubmit(submit)}
        noValidate
      >
        <section className="card p-6">
          <h2 className="mb-6 font-semibold">Payment information</h2>
          <div className="grid gap-5 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <FormField label="Invoice" error={e.invoiceId?.message}>
                <select
                  className="input"
                  value={selectedInvoiceId || ""}
                  onChange={(ev) => {
                    const id = Number(ev.target.value);
                    form.setValue("invoiceId", id, { shouldValidate: true });
                    const found = payableInvoices.data?.content.find(
                      (i) => i.id === id,
                    );
                    if (found) {
                      form.setValue(
                        "amount",
                        Number(found.outstandingAmount ?? found.total),
                        { shouldValidate: true },
                      );
                    }
                  }}
                >
                  <option value="">Select a confirmed invoice…</option>
                  {payableInvoices.data?.content.map((inv) => (
                    <option key={inv.id} value={inv.id}>
                      {inv.invoiceNumber} · {inv.customerName} · Outstanding:{" "}
                      {money(inv.outstandingAmount ?? inv.total)} (Total:{" "}
                      {money(inv.total)})
                    </option>
                  ))}
                  {Boolean(selectedInvoiceId) &&
                    !payableInvoices.data?.content.some(
                      (i) => i.id === selectedInvoiceId,
                    ) &&
                    selectedInvoice && (
                      <option value={selectedInvoice.id}>
                        {selectedInvoice.invoiceNumber} ·{" "}
                        {selectedInvoice.customerName} · Outstanding:{" "}
                        {money(
                          selectedInvoice.outstandingAmount ??
                            selectedInvoice.total,
                        )}
                      </option>
                    )}
                </select>
              </FormField>
            </div>

            {selectedInvoice && (
              <div className="rounded-xl border border-violet-100 bg-violet-50/70 p-4 text-xs leading-6 text-violet-900 sm:col-span-2">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <span className="font-semibold">
                      {selectedInvoice.invoiceNumber}
                    </span>{" "}
                    · Customer: {selectedInvoice.customerName} · Due:{" "}
                    {selectedInvoice.dueDate}
                  </div>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      form.setValue(
                        "amount",
                        Number(
                          selectedInvoice.outstandingAmount ??
                            selectedInvoice.total,
                        ),
                        { shouldValidate: true },
                      )
                    }
                  >
                    Fill remaining balance (
                    {money(
                      selectedInvoice.outstandingAmount ??
                        selectedInvoice.total,
                    )}
                    )
                  </Button>
                </div>
                <div className="mt-2 flex flex-wrap gap-6 text-slate-600">
                  <span>Invoice Total: {money(selectedInvoice.total)}</span>
                  <span>
                    Paid So Far: {money(selectedInvoice.amountPaid || 0)}
                  </span>
                  <span className="font-semibold text-violet-800">
                    Remaining Balance:{" "}
                    {money(
                      selectedInvoice.outstandingAmount ??
                        selectedInvoice.total,
                    )}
                  </span>
                </div>
              </div>
            )}

            <FormField label="Payment method" error={e.method?.message}>
              <select className="input" {...form.register("method")}>
                {paymentMethods.map((m) => (
                  <option key={m} value={m}>
                    {human(m)}
                  </option>
                ))}
              </select>
            </FormField>

            <FormField label="Payment amount" error={e.amount?.message}>
              <input
                type="number"
                step="0.01"
                min="0.01"
                className="input"
                {...form.register("amount", { valueAsNumber: true })}
              />
            </FormField>

            <FormField label="Payment date" error={e.paymentDate?.message}>
              <input
                type="date"
                className="input"
                {...form.register("paymentDate")}
              />
            </FormField>

            <FormField
              label="Payment reference (optional)"
              error={e.paymentReference?.message}
              hint="Leave blank to auto-generate a unique reference."
            >
              <input
                className="input"
                placeholder="e.g. UTR-98412041 or UPI-7781"
                {...form.register("paymentReference")}
              />
            </FormField>

            <div className="sm:col-span-2">
              <FormField label="Note (optional)" error={e.note?.message}>
                <textarea
                  className="input min-h-20"
                  placeholder="Optional reconciliation notes…"
                  {...form.register("note")}
                />
              </FormField>
            </div>
          </div>

          <FormActions
            pending={form.formState.isSubmitting}
            cancel="/payments"
            label="Record payment"
          />
        </section>
      </form>
    </>
  );
}
