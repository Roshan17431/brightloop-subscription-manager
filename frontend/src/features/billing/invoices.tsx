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
  type Invoice,
  type Page,
  type User,
} from "@/lib/types";
import {
  Badge,
  DetailValue,
  Empty,
  ErrorState,
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
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ["invoices", { q, state, page }],
    queryFn: ({ signal }) =>
      api<Page<Invoice>>(
        `/invoices${queryString({ q, state, page, size: 10 })}`,
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
            ? "Review your subscription invoices, download PDFs, and print copies."
            : "Invoices generated from recurring subscriptions, with snapshotted prices, discounts, and taxes."
        }
        action={
          user.role !== "CUSTOMER" && (
            <Button variant="outline" asChild>
              <Link href="/billing">
                <FileText />
                Billing runs
              </Link>
            </Button>
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
            aria-label="Filter invoice state"
            className="input sm:ml-auto sm:!w-44"
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
              q || state ? "No matching invoices" : "No invoices generated yet"
            }
            description={
              q || state
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
                    <th>Billing period</th>
                    <th>Issue date</th>
                    <th>Due date</th>
                    <th>State</th>
                    <th>Total</th>
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
                      <td className="text-xs text-slate-500">
                        {inv.billingPeriodStart} → {inv.billingPeriodEnd}
                      </td>
                      <td className="text-slate-500">{inv.issueDate}</td>
                      <td className="text-slate-500">{inv.dueDate}</td>
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
  const [downloading, setDownloading] = useState(false);
  const client = useQueryClient();
  const result = useQuery({
    queryKey: ["invoices", id],
    queryFn: ({ signal }) => api<Invoice>(`/invoices/${id}`, { signal }),
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
              (inv.state === "DRAFT" || inv.state === "CONFIRMED") && (
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
          </div>
        }
      />
      <div className="no-print card mb-6 flex w-full min-w-0 max-w-full gap-1 overflow-x-auto p-5">
        {inv.state === "CANCELLED" ? (
          <div className="flex items-center gap-3 text-xs font-semibold text-rose-600">
            <Ban size={15} />
            This invoice was cancelled
            {inv.cancelledAt
              ? ` on ${inv.cancelledAt.slice(0, 10)}`
              : ""}
            .
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
            {inv.sentAt ? inv.sentAt.replace("T", " ").slice(0, 19) : "Not sent"}
          </DetailValue>
          <DetailValue label="Total due">{money(inv.total)}</DetailValue>
        </dl>
      </section>

      <section className="card overflow-hidden">
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
            <div className="flex justify-between border-t border-slate-200 pt-3 text-base font-semibold text-slate-900">
              <span>Total due</span>
              <span>{money(inv.total)}</span>
            </div>
          </div>
        </div>
      </section>

      <p className="no-print mt-4 text-xs text-slate-400">
        Invoices move from Draft to Confirmed. Marking an invoice as Paid
        requires recorded payment evidence in Phase 3.
      </p>

      <ConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title={`Cancel ${inv.invoiceNumber}?`}
        pending={transition.isPending}
        onConfirm={() => transition.mutate("CANCELLED")}
      >
        Cancelling this invoice marks it as void. Cancelled invoices cannot be
        confirmed or emailed.
      </ConfirmDialog>
    </>
  );
}
