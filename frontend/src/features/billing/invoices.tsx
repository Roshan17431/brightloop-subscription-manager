"use client";
import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  Ban,
  Check,
  ChevronRight,
  Clock,
  CreditCard,
  Download,
  FileText,
  Mail,
  Printer,
} from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage, queryString } from "@/lib/api";
import { human, money } from "@/lib/utils";
import {
  invoiceStates,
  paymentMethods,
  type Invoice,
  type Page,
  type Payment,
  type PaymentMethod,
  type User,
} from "@/lib/types";
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
import { ConfirmDialog } from "@/components/ui/dialog";

export function InvoiceList({ user }: { user: User }) {
  const [q, setQ] = useState("");
  const [state, setState] = useState("");
  const [balanceFilter, setBalanceFilter] = useState("");
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ["invoices", { q, state, balanceFilter, page }],
    queryFn: ({ signal }) =>
      api<Page<Invoice>>(
        `/invoices${queryString({
          q,
          state: state || undefined,
          outstanding: balanceFilter === "OUTSTANDING" ? true : undefined,
          overdue: balanceFilter === "OVERDUE" ? true : undefined,
          partiallyPaid: balanceFilter === "PARTIALLY_PAID" ? true : undefined,
          page,
          size: 10,
        })}`,
        { signal },
      ),
  });
  return (
    <>
      <Heading
        eyebrow={
          user.role === "CUSTOMER" ? "Your billing" : "Recurring invoicing"
        }
        title="Invoices"
        description={
          user.role === "CUSTOMER"
            ? "Review your subscription invoices, payment balances, download PDFs, and print copies."
            : "Invoices generated from recurring subscriptions, with snapshotted prices, discounts, taxes, and payment reconciliation."
        }
        action={
          user.role !== "CUSTOMER" && (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" asChild>
                <Link href="/payments">
                  <CreditCard />
                  Payments
                </Link>
              </Button>
              <Button variant="outline" asChild>
                <Link href="/billing">
                  <FileText />
                  Billing runs
                </Link>
              </Button>
            </div>
          )
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
            placeholder="Search invoice, subscription, customer…"
          />
          <select
            aria-label="Filter invoice balance"
            className="input sm:ml-auto sm:!w-48"
            value={balanceFilter}
            onChange={(e) => {
              setBalanceFilter(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All balances</option>
            <option value="OUTSTANDING">Outstanding balance</option>
            <option value="OVERDUE">Overdue only</option>
            <option value="PARTIALLY_PAID">Partially paid</option>
          </select>
          <select
            aria-label="Filter invoice state"
            className="input sm:!w-44"
            value={state}
            onChange={(e) => {
              setState(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All states</option>
            {invoiceStates.map((s) => (
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
          <div className="p-5">
            <ErrorState error={result.error} retry={() => result.refetch()} />
          </div>
        ) : !result.data.content.length ? (
          <Empty
            title={
              q || state || balanceFilter
                ? "No matching invoices"
                : "No invoices generated yet"
            }
            description={
              q || state || balanceFilter
                ? "Try another search or state filter."
                : user.role === "CUSTOMER"
                  ? "Invoices will appear here when your active subscriptions are billed."
                  : "Run recurring billing on active subscriptions to generate invoices automatically."
            }
            action={
              user.role === "ADMIN" ? (
                <Button asChild>
                  <Link href="/billing">Go to billing execution</Link>
                </Button>
              ) : undefined
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Invoice</th>
                    <th>Subscription</th>
                    {user.role !== "CUSTOMER" && <th>Customer</th>}
                    <th>Due date</th>
                    <th>State</th>
                    <th>Payment status</th>
                    <th>Total</th>
                    <th>Outstanding</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((inv) => (
                    <tr key={inv.id}>
                      <td>
                        <Link href={`/invoices/${inv.id}`}>
                          {inv.invoiceNumber}
                        </Link>
                      </td>
                      <td>
                        <Link
                          href={`/subscriptions/${inv.subscriptionId}`}
                          className="text-xs text-slate-500"
                        >
                          {inv.subscriptionNumber}
                        </Link>
                      </td>
                      {user.role !== "CUSTOMER" && (
                        <td className="text-slate-500">{inv.customerName}</td>
                      )}
                      <td className="text-slate-500">
                        <div>{inv.dueDate}</div>
                        {inv.overdue && (
                          <span className="mt-0.5 inline-flex items-center gap-1 text-[11px] font-semibold text-amber-600">
                            <Clock className="size-3" />
                            {inv.daysOverdue}d overdue
                          </span>
                        )}
                      </td>
                      <td>
                        <Badge value={inv.state} />
                      </td>
                      <td>
                        <Badge value={inv.paymentStatus || inv.state} />
                      </td>
                      <td className="font-medium">{money(inv.total)}</td>
                      <td className="font-medium">
                        {money(
                          inv.outstandingAmount ??
                            (inv.state === "PAID" || inv.state === "CANCELLED"
                              ? 0
                              : inv.total),
                        )}
                      </td>
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

export function InvoiceDetail({ id, user }: { id: string; user: User }) {
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);
  const [payAmount, setPayAmount] = useState("");
  const [payMethod, setPayMethod] = useState<PaymentMethod>("BANK_TRANSFER");
  const [payDate, setPayDate] = useState(
    () => new Date().toISOString().slice(0, 10),
  );
  const [payRef, setPayRef] = useState("");
  const [payNote, setPayNote] = useState("");
  const [downloading, setDownloading] = useState(false);

  const client = useQueryClient();
  const result = useQuery({
    queryKey: ["invoices", id],
    queryFn: ({ signal }) => api<Invoice>(`/invoices/${id}`, { signal }),
  });

  const invoicePayments = useQuery({
    queryKey: ["payments", "invoice", id],
    queryFn: ({ signal }) =>
      api<Payment[]>(`/invoices/${id}/payments`, { signal }),
  });

  const transition = useMutation({
    mutationFn: (state: string) =>
      api<Invoice>(`/invoices/${id}/transition`, {
        method: "POST",
        body: { state },
      }),
    onSuccess: (updated) => {
      client.invalidateQueries({ queryKey: ["invoices"] });
      setConfirmCancel(false);
      toast.success(`Invoice moved to ${human(updated.state)}.`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const settleZero = useMutation({
    mutationFn: () =>
      api<Invoice>(`/invoices/${id}/settle-zero`, {
        method: "POST",
        body: {},
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["invoices"] });
      toast.success("Zero-value invoice settled as Paid.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const recordPayment = useMutation({
    mutationFn: () =>
      api<Payment>(`/invoices/${id}/payments`, {
        method: "POST",
        body: {
          invoiceId: Number(id),
          method: payMethod,
          amount: Number(payAmount).toFixed(2),
          paymentDate: payDate,
          paymentReference: payRef.trim() || undefined,
          note: payNote.trim() || undefined,
        },
      }),
    onSuccess: (saved) => {
      client.invalidateQueries({ queryKey: ["invoices"] });
      client.invalidateQueries({ queryKey: ["payments"] });
      setRecordOpen(false);
      setPayRef("");
      setPayNote("");
      toast.success(
        saved.invoiceState === "PAID"
          ? `Payment recorded. Invoice is now PAID!`
          : `Payment of ${money(saved.amount)} recorded.`,
      );
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const sendEmail = useMutation({
    mutationFn: () =>
      api<Invoice>(`/invoices/${id}/send`, {
        method: "POST",
        body: {},
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["invoices"] });
      toast.success("Invoice email delivered.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  async function downloadPdf(invoiceNumber: string) {
    setDownloading(true);
    try {
      const base = process.env.NEXT_PUBLIC_API_URL || "/api/v1";
      const response = await fetch(`${base}/invoices/${id}/pdf`, {
        credentials: "include",
      });
      if (!response.ok) {
        throw new Error(`Could not download PDF (${response.status}).`);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${invoiceNumber}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast.success("Invoice PDF downloaded.");
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setDownloading(false);
    }
  }

  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const inv = result.data;
  const flowStates = ["DRAFT", "CONFIRMED", "PAID"] as const;
  const flowIndex = flowStates.indexOf(
    inv.state as (typeof flowStates)[number],
  );
  const amountPaid = Number(inv.amountPaid ?? 0);
  const outstandingAmount = Number(
    inv.outstandingAmount ??
      (inv.state === "PAID" || inv.state === "CANCELLED"
        ? 0
        : Number(inv.total) - amountPaid),
  );
  const paymentStatus = inv.paymentStatus || inv.state;

  return (
    <>
      <Link
        href="/invoices"
        className="no-print mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to invoices
      </Link>
      <Heading
        eyebrow="Invoice details"
        title={inv.invoiceNumber}
        description={`${inv.customerName} · ${inv.planName} (${human(inv.billingPeriod)})`}
        action={
          <div className="no-print flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => window.print()}>
              <Printer />
              Print
            </Button>
            <Button
              variant="outline"
              disabled={downloading}
              onClick={() => downloadPdf(inv.invoiceNumber)}
            >
              <Download />
              {downloading ? "Exporting…" : "Export PDF"}
            </Button>
            {user.role !== "CUSTOMER" && inv.state !== "CANCELLED" && (
              <Button
                variant="outline"
                disabled={sendEmail.isPending}
                onClick={() => sendEmail.mutate()}
              >
                <Mail />
                {sendEmail.isPending ? "Sending…" : "Email invoice"}
              </Button>
            )}
            {user.role !== "CUSTOMER" &&
              (inv.state === "DRAFT" ||
                (inv.state === "CONFIRMED" && amountPaid === 0)) && (
                <Button
                  variant="outline"
                  disabled={transition.isPending}
                  onClick={() => setConfirmCancel(true)}
                >
                  <Ban />
                  Cancel invoice
                </Button>
              )}
            {user.role !== "CUSTOMER" && inv.state === "DRAFT" && (
              <Button
                disabled={transition.isPending}
                onClick={() => transition.mutate("CONFIRMED")}
              >
                {transition.isPending ? "Confirming…" : "Confirm invoice"}
                <ArrowRight />
              </Button>
            )}
            {user.role !== "CUSTOMER" &&
              inv.state === "CONFIRMED" &&
              Number(inv.total) === 0 && (
                <Button
                  disabled={settleZero.isPending}
                  onClick={() => settleZero.mutate()}
                >
                  {settleZero.isPending ? "Settling…" : "Settle zero invoice"}
                </Button>
              )}
            {user.role !== "CUSTOMER" &&
              inv.state === "CONFIRMED" &&
              outstandingAmount > 0 && (
                <Button
                  onClick={() => {
                    setPayAmount(outstandingAmount.toFixed(2));
                    setRecordOpen(true);
                  }}
                >
                  <CreditCard />
                  Record payment
                </Button>
              )}
          </div>
        }
      />

      <div className="no-print mb-6 grid gap-4 sm:grid-cols-4">
        <div className="card p-4">
          <p className="text-xs text-slate-400">Invoice total</p>
          <p className="mt-1 text-xl font-semibold">{money(inv.total)}</p>
        </div>
        <div className="card p-4">
          <p className="text-xs text-slate-400">Total paid</p>
          <p className="mt-1 text-xl font-semibold text-emerald-600">
            {money(amountPaid)}
          </p>
        </div>
        <div className="card p-4">
          <p className="text-xs text-slate-400">Outstanding balance</p>
          <p className="mt-1 text-xl font-semibold text-slate-900">
            {money(outstandingAmount)}
          </p>
        </div>
        <div className="card p-4">
          <p className="text-xs text-slate-400">Payment status</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <Badge value={paymentStatus} />
            {inv.overdue && (
              <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-semibold text-amber-700">
                <Clock className="size-3" />
                {inv.daysOverdue}d overdue
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="no-print card mb-6 flex w-full min-w-0 max-w-full gap-1 overflow-x-auto p-5">
        {inv.state === "CANCELLED" ? (
          <div className="flex items-center gap-3 text-xs font-semibold text-rose-600">
            <Ban size={15} />
            This invoice was cancelled
            {inv.cancelledAt ? ` on ${inv.cancelledAt.slice(0, 10)}` : ""}.
          </div>
        ) : (
          flowStates.map((st, i) => (
            <div className="flex min-w-fit flex-1 items-center gap-2" key={st}>
              <span
                className={`flex size-6 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold ${i <= flowIndex ? "bg-violet-600 text-white" : "bg-slate-100 text-slate-400"}`}
              >
                {i < flowIndex ? <Check size={12} /> : i + 1}
              </span>
              <span
                className={`text-xs ${i === flowIndex ? "font-semibold text-violet-700" : "text-slate-500"}`}
              >
                {human(st)}
              </span>
              {i < flowStates.length - 1 && (
                <ChevronRight className="mx-auto size-4 text-slate-300" />
              )}
            </div>
          ))
        )}
      </div>

      <section className="card mb-6 p-6">
        <div className="mb-7 flex items-center justify-between">
          <div>
            <h2 className="font-semibold">Invoice overview</h2>
            <p className="mt-1 text-xs text-slate-400">
              Historical snapshot generated from subscription{" "}
              <Link
                href={`/subscriptions/${inv.subscriptionId}`}
                className="font-semibold text-violet-600"
              >
                {inv.subscriptionNumber}
              </Link>
            </p>
          </div>
          <Badge value={inv.state} />
        </div>
        <dl className="grid gap-7 sm:grid-cols-3">
          <DetailValue label="Customer">{inv.customerName}</DetailValue>
          <DetailValue label="Customer email">{inv.customerEmail}</DetailValue>
          <DetailValue label="Company">{inv.customerCompany}</DetailValue>
          <DetailValue label="Billing address">
            {inv.billingAddress}
          </DetailValue>
          <DetailValue label="Plan">
            {inv.planName} ({human(inv.billingPeriod)})
          </DetailValue>
          <DetailValue label="Billing period">
            {inv.billingPeriodStart} → {inv.billingPeriodEnd}
          </DetailValue>
          <DetailValue label="Issue date">{inv.issueDate}</DetailValue>
          <DetailValue label="Due date">{inv.dueDate}</DetailValue>
          <DetailValue label="Payment terms">{inv.paymentTerms}</DetailValue>
          <DetailValue label="Applied discount">
            {inv.discountName
              ? `${inv.discountName} (-${money(inv.discountAmount)})`
              : "None"}
          </DetailValue>
          <DetailValue label="Last emailed">
            {inv.sentAt
              ? inv.sentAt.replace("T", " ").slice(0, 19)
              : "Not sent"}
          </DetailValue>
          <DetailValue label="Total due">{money(inv.total)}</DetailValue>
          {inv.followupNotes && (
            <DetailValue label="Overdue follow-up">
              {inv.followupNotes}
              {inv.followupBy ? ` (${inv.followupBy})` : ""}
            </DetailValue>
          )}
        </dl>
      </section>

      <section className="card mb-6 overflow-hidden">
        <div className="border-b border-slate-100 p-5">
          <h2 className="font-semibold">Invoice lines</h2>
          <p className="mt-1 text-xs text-slate-400">
            Unit prices, discounts, and tax rules are snapshotted at billing
            time.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Variant</th>
                <th>Qty</th>
                <th>Unit price</th>
                <th>Subtotal</th>
                <th>Discount</th>
                <th>Tax</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {inv.lines.map((line) => (
                <tr key={line.id}>
                  <td className="font-medium">{line.productName}</td>
                  <td className="text-slate-500">
                    {line.variantName || "Base product"}
                  </td>
                  <td>{line.quantity}</td>
                  <td>{money(line.unitPrice)}</td>
                  <td>{money(line.subtotal)}</td>
                  <td className="text-slate-500">
                    {Number(line.discountAmount) > 0
                      ? `-${money(line.discountAmount)}`
                      : "0.00"}
                  </td>
                  <td className="text-slate-500">
                    {money(line.taxAmount)}{" "}
                    <span className="text-xs text-slate-400">
                      ({line.taxRate}%
                      {line.taxRuleName ? ` · ${line.taxRuleName}` : ""})
                    </span>
                  </td>
                  <td className="font-medium">{money(line.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="border-t border-slate-100 bg-slate-50/50 p-6">
          <div className="ml-auto max-w-xs space-y-2 text-sm">
            <div className="flex justify-between text-slate-500">
              <span>Subtotal</span>
              <span>{money(inv.subtotal)}</span>
            </div>
            <div className="flex justify-between text-slate-500">
              <span>
                Discount{inv.discountName ? ` (${inv.discountName})` : ""}
              </span>
              <span>-{money(inv.discountAmount)}</span>
            </div>
            <div className="flex justify-between text-slate-500">
              <span>Taxes</span>
              <span>{money(inv.taxAmount)}</span>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-2 font-medium text-slate-700">
              <span>Invoice total</span>
              <span>{money(inv.total)}</span>
            </div>
            <div className="flex justify-between text-emerald-600">
              <span>Amount paid</span>
              <span>{money(amountPaid)}</span>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-3 text-base font-semibold text-slate-900">
              <span>Outstanding balance</span>
              <span>{money(outstandingAmount)}</span>
            </div>
          </div>
        </div>
      </section>

      <section className="no-print card overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 p-5">
          <div>
            <h2 className="font-semibold">Payment history</h2>
            <p className="mt-1 text-xs text-slate-400">
              Recorded payments and reversals reconciled against this invoice.
            </p>
          </div>
          {user.role !== "CUSTOMER" &&
            inv.state === "CONFIRMED" &&
            outstandingAmount > 0 && (
              <Button
                size="sm"
                onClick={() => {
                  setPayAmount(outstandingAmount.toFixed(2));
                  setRecordOpen(true);
                }}
              >
                <CreditCard />
                Record payment
              </Button>
            )}
        </div>
        {invoicePayments.isPending ? (
          <div className="p-5">
            <Loading />
          </div>
        ) : invoicePayments.error ? (
          <div className="p-5">
            <ErrorState
              error={invoicePayments.error}
              retry={() => invoicePayments.refetch()}
            />
          </div>
        ) : !invoicePayments.data?.length ? (
          <Empty
            title="No payments recorded for this invoice"
            description={
              inv.state === "DRAFT"
                ? "Confirm this invoice first before recording customer payments."
                : "Record a full or partial payment to reconcile the outstanding balance."
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Method</th>
                  <th>Date</th>
                  <th>Recorded by</th>
                  <th>Status</th>
                  <th>Amount</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {invoicePayments.data.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <Link href={`/payments/${p.id}`}>
                        {p.paymentReference}
                      </Link>
                    </td>
                    <td className="text-slate-500">{human(p.method)}</td>
                    <td className="text-slate-500">{p.paymentDate}</td>
                    <td className="text-slate-500">{p.recordedBy}</td>
                    <td>
                      <Badge value={p.status} />
                    </td>
                    <td className="font-medium">{money(p.amount)}</td>
                    <td>
                      <Link
                        href={`/payments/${p.id}`}
                        aria-label={`View payment ${p.paymentReference}`}
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

      <ConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title={`Cancel ${inv.invoiceNumber}?`}
        pending={transition.isPending}
        onConfirm={() => transition.mutate("CANCELLED")}
      >
        Cancelling this invoice marks it as void. Cancelled invoices cannot be
        confirmed, emailed, or paid.
      </ConfirmDialog>

      <ConfirmDialog
        open={recordOpen}
        onOpenChange={setRecordOpen}
        title={`Record payment for ${inv.invoiceNumber}`}
        confirmLabel="Record payment"
        pendingLabel="Recording…"
        variant="default"
        pending={recordPayment.isPending}
        onConfirm={() => {
          const val = Number(payAmount);
          if (!val || val <= 0) {
            toast.error("Enter a valid positive payment amount.");
            return;
          }
          recordPayment.mutate();
        }}
      >
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            Outstanding balance:{" "}
            <span className="font-semibold text-slate-800">
              {money(outstandingAmount)}
            </span>{" "}
            (Invoice total: {money(inv.total)}).
          </p>
          <FormField label="Payment method">
            <select
              aria-label="Payment method"
              className="input"
              value={payMethod}
              onChange={(e) => setPayMethod(e.target.value as PaymentMethod)}
            >
              {paymentMethods.map((m) => (
                <option key={m} value={m}>
                  {human(m)}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Amount">
            <input
              aria-label="Payment amount"
              type="number"
              step="0.01"
              min="0.01"
              max={outstandingAmount}
              className="input"
              value={payAmount}
              onChange={(e) => setPayAmount(e.target.value)}
            />
          </FormField>
          <FormField label="Payment date">
            <input
              aria-label="Payment date"
              type="date"
              className="input"
              value={payDate}
              onChange={(e) => setPayDate(e.target.value)}
            />
          </FormField>
          <FormField label="Payment reference (optional)">
            <input
              aria-label="Payment reference"
              className="input"
              placeholder="UTR / UPI / Check reference…"
              value={payRef}
              onChange={(e) => setPayRef(e.target.value)}
            />
          </FormField>
          <FormField label="Note (optional)">
            <input
              aria-label="Payment note"
              className="input"
              placeholder="Optional note…"
              value={payNote}
              onChange={(e) => setPayNote(e.target.value)}
            />
          </FormField>
        </div>
      </ConfirmDialog>
    </>
  );
}
