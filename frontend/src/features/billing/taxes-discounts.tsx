"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, ArrowUpRight, Pencil, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage, queryString } from "@/lib/api";
import { human, money } from "@/lib/utils";
import type {
  Discount,
  Page,
  Product,
  Subscription,
  TaxRule,
  User,
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
import { discountSchema, taxRuleSchema } from "./schemas";

export function TaxList({ user }: { user: User }) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ["taxes", { q, status, page }],
    queryFn: ({ signal }) =>
      api<Page<TaxRule>>(
        `/taxes${queryString({ q, status, page, size: 10 })}`,
        { signal },
      ),
  });
  return (
    <>
      <Heading
        eyebrow="Financial rules"
        title="Tax rules"
        description="Configure automatic percentage tax rules applied during recurring billing."
        action={
          user.role === "ADMIN" && (
            <NewButton href="/taxes/new">New tax rule</NewButton>
          )
        }
      />
      <section className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-5">
          <SearchField
            value={q}
            onChange={(v) => {
              setQ(v);
              setPage(0);
            }}
            placeholder="Search tax rules…"
          />
          <select
            aria-label="Filter tax status"
            className="input sm:ml-auto sm:!w-40"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option>ACTIVE</option>
            <option>INACTIVE</option>
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
              q || status ? "No matching tax rules" : "No tax rules configured"
            }
            description="Create a tax rule to automatically apply percentage taxes to invoice lines."
            action={
              user.role === "ADMIN" ? (
                <NewButton href="/taxes/new">Create tax rule</NewButton>
              ) : undefined
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Rule</th>
                    <th>Rate</th>
                    <th>Applicability</th>
                    <th>Effective dates</th>
                    <th>Status</th>
                    <th className="!text-right">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((t) => (
                    <tr key={t.id}>
                      <td>
                        <Link href={`/taxes/${t.id}`}>{t.name}</Link>
                      </td>
                      <td className="font-medium">{t.rate}%</td>
                      <td className="text-slate-500">
                        {t.productName
                          ? `Product: ${t.productName}`
                          : t.productType
                            ? `Type: ${t.productType}`
                            : t.isDefault
                              ? "Default (All products)"
                              : "General"}
                      </td>
                      <td className="text-slate-500">
                        {t.effectiveFrom || "Any"} → {t.effectiveUntil || "Any"}
                      </td>
                      <td>
                        <Badge value={t.status} />
                      </td>
                      <td className="text-right">
                        <Link
                          href={`/taxes/${t.id}`}
                          aria-label={`View ${t.name}`}
                        >
                          <ArrowUpRight className="ml-auto size-4 text-slate-400" />
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

export function TaxDetail({ id, user }: { id: string; user: User }) {
  const [confirm, setConfirm] = useState(false);
  const client = useQueryClient();
  const router = useRouter();
  const result = useQuery({
    queryKey: ["taxes", id],
    queryFn: ({ signal }) => api<TaxRule>(`/taxes/${id}`, { signal }),
  });
  const remove = useMutation({
    mutationFn: () => api(`/taxes/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["taxes"] });
      toast.success("Tax rule deleted.");
      router.push("/taxes");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const t = result.data;
  return (
    <>
      <Link
        href="/taxes"
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to tax rules
      </Link>
      <Heading
        eyebrow="Tax rule details"
        title={t.name}
        description={`Rate: ${t.rate}%`}
        action={
          user.role === "ADMIN" && (
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setConfirm(true)}>
                <Trash2 />
                Delete
              </Button>
              <Button asChild>
                <Link href={`/taxes/${id}/edit`}>
                  <Pencil />
                  Edit tax rule
                </Link>
              </Button>
            </div>
          )
        }
      />
      <section className="card p-6">
        <div className="mb-7 flex items-center justify-between">
          <h2 className="font-semibold">Rule configuration</h2>
          <Badge value={t.status} />
        </div>
        <dl className="grid gap-7 sm:grid-cols-3">
          <DetailValue label="Tax rate">{t.rate}%</DetailValue>
          <DetailValue label="Target product">
            {t.productName || "All products"}
          </DetailValue>
          <DetailValue label="Product type">
            {t.productType || "All types"}
          </DetailValue>
          <DetailValue label="Default rule">
            {t.isDefault ? "Yes" : "No"}
          </DetailValue>
          <DetailValue label="Effective from">
            {t.effectiveFrom || "Immediate"}
          </DetailValue>
          <DetailValue label="Effective until">
            {t.effectiveUntil || "No expiry"}
          </DetailValue>
          <div className="sm:col-span-3">
            <DetailValue label="Description">{t.description}</DetailValue>
          </div>
        </dl>
      </section>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title={`Delete ${t.name}?`}
        pending={remove.isPending}
        onConfirm={() => remove.mutate()}
      >
        This removes the tax rule. Existing invoices retain their snapshotted
        tax rates.
      </ConfirmDialog>
    </>
  );
}

export function TaxEditor({ id }: { id?: string }) {
  const result = useQuery({
    queryKey: ["taxes", id],
    queryFn: ({ signal }) => api<TaxRule>(`/taxes/${id}`, { signal }),
    enabled: !!id,
  });
  if (id && result.isPending) return <Loading />;
  if (result.error) return <ErrorState error={result.error} />;
  return <TaxForm key={id || "new"} initial={result.data} />;
}

function TaxForm({ initial }: { initial?: TaxRule }) {
  const router = useRouter();
  const client = useQueryClient();
  const products = useQuery({
    queryKey: ["products", "tax-picker"],
    queryFn: ({ signal }) =>
      api<Page<Product>>("/products?size=100", { signal }),
  });
  const form = useForm<z.infer<typeof taxRuleSchema>>({
    resolver: zodResolver(taxRuleSchema) as never,
    defaultValues: initial
      ? {
          name: initial.name,
          description: initial.description || "",
          rate: Number(initial.rate),
          productType: initial.productType || "",
          productId: initial.productId || undefined,
          isDefault: initial.isDefault,
          effectiveFrom: initial.effectiveFrom || "",
          effectiveUntil: initial.effectiveUntil || "",
          status: initial.status,
        }
      : {
          name: "",
          description: "",
          rate: 18,
          productType: "",
          productId: undefined,
          isDefault: true,
          effectiveFrom: "",
          effectiveUntil: "",
          status: "ACTIVE",
        },
  });
  const e = form.formState.errors;
  async function save(values: z.infer<typeof taxRuleSchema>) {
    try {
      const body = {
        ...values,
        productType: values.productType?.trim() || null,
        productId: values.productId || null,
        effectiveFrom: values.effectiveFrom || null,
        effectiveUntil: values.effectiveUntil || null,
      };
      const saved = await api<TaxRule>(
        initial ? `/taxes/${initial.id}` : "/taxes",
        { method: initial ? "PUT" : "POST", body },
      );
      await client.invalidateQueries({ queryKey: ["taxes"] });
      toast.success(initial ? "Tax rule updated." : "Tax rule created.");
      router.push(`/taxes/${saved.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }
  return (
    <>
      <Heading
        eyebrow="Financial rules"
        title={initial ? "Edit tax rule" : "Create tax rule"}
        description="Define percentage tax rates for specific products, product types, or default workspace billing."
      />
      <form
        className="card max-w-4xl p-6 sm:p-8"
        onSubmit={form.handleSubmit(save)}
        noValidate
      >
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField label="Tax rule name" error={e.name?.message}>
            <input
              className="input"
              placeholder="e.g. Standard GST 18%"
              {...form.register("name")}
            />
          </FormField>
          <FormField label="Tax rate (%)" error={e.rate?.message}>
            <input
              className="input"
              type="number"
              min="0"
              max="100"
              step="0.01"
              {...form.register("rate", { valueAsNumber: true })}
            />
          </FormField>
          <FormField
            label="Specific product (optional)"
            error={e.productId?.message}
          >
            <select
              className="input"
              value={form.watch("productId") || ""}
              onChange={(ev) =>
                form.setValue(
                  "productId",
                  ev.target.value ? Number(ev.target.value) : undefined,
                )
              }
            >
              <option value="">All products</option>
              {products.data?.content.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField
            label="Product type filter (optional)"
            error={e.productType?.message}
          >
            <input
              className="input"
              placeholder="e.g. SOFTWARE, SERVICE"
              {...form.register("productType")}
            />
          </FormField>
          <FormField label="Effective from" error={e.effectiveFrom?.message}>
            <input
              className="input"
              type="date"
              {...form.register("effectiveFrom")}
            />
          </FormField>
          <FormField label="Effective until" error={e.effectiveUntil?.message}>
            <input
              className="input"
              type="date"
              {...form.register("effectiveUntil")}
            />
          </FormField>
          <FormField label="Status">
            <select className="input" {...form.register("status")}>
              <option>ACTIVE</option>
              <option>INACTIVE</option>
            </select>
          </FormField>
          <div className="flex items-end pb-2">
            <label className="flex items-center gap-3 rounded-lg border border-slate-200 px-4 py-2.5 text-sm">
              <input
                type="checkbox"
                className="size-4 accent-violet-600"
                {...form.register("isDefault")}
              />
              Use as default general tax rule
            </label>
          </div>
          <div className="sm:col-span-2">
            <FormField label="Description">
              <textarea className="input" {...form.register("description")} />
            </FormField>
          </div>
        </div>
        <FormActions
          pending={form.formState.isSubmitting}
          cancel={initial ? `/taxes/${initial.id}` : "/taxes"}
          label={initial ? "Save tax rule" : "Create tax rule"}
        />
      </form>
    </>
  );
}

export function DiscountList({ user }: { user: User }) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ["discounts", { q, status, page }],
    queryFn: ({ signal }) =>
      api<Page<Discount>>(
        `/discounts${queryString({ q, status, page, size: 10 })}`,
        { signal },
      ),
  });
  return (
    <>
      <Heading
        eyebrow="Financial rules"
        title="Discounts"
        description="Configure percentage or fixed discount rules with thresholds, validity dates, and usage limits."
        action={
          user.role === "ADMIN" && (
            <NewButton href="/discounts/new">New discount</NewButton>
          )
        }
      />
      <section className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-5">
          <SearchField
            value={q}
            onChange={(v) => {
              setQ(v);
              setPage(0);
            }}
            placeholder="Search discounts or codes…"
          />
          <select
            aria-label="Filter discount status"
            className="input sm:ml-auto sm:!w-40"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option>ACTIVE</option>
            <option>INACTIVE</option>
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
              q || status ? "No matching discounts" : "No discounts configured"
            }
            description="Create a discount rule to automatically reward seat volume or minimum purchase amounts."
            action={
              user.role === "ADMIN" ? (
                <NewButton href="/discounts/new">Create discount</NewButton>
              ) : undefined
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Discount</th>
                    <th>Value</th>
                    <th>Scope</th>
                    <th>Thresholds</th>
                    <th>Usage</th>
                    <th>Status</th>
                    <th className="!text-right">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((d) => (
                    <tr key={d.id}>
                      <td>
                        <Link href={`/discounts/${d.id}`}>{d.name}</Link>
                        {d.code && (
                          <p className="mt-1 text-[10px] font-mono text-slate-400">
                            {d.code}
                          </p>
                        )}
                      </td>
                      <td className="font-medium">
                        {d.type === "PERCENTAGE"
                          ? `${d.value}%`
                          : money(d.value)}
                      </td>
                      <td className="text-slate-500">
                        {d.scope === "PRODUCT"
                          ? `Product: ${d.productName || `#${d.productId}`}`
                          : d.subscriptionNumber
                            ? `Sub: ${d.subscriptionNumber}`
                            : "All subscriptions"}
                      </td>
                      <td className="text-xs text-slate-500">
                        {d.minimumQuantity ? `Min qty ${d.minimumQuantity}` : ""}
                        {d.minimumQuantity && d.minimumPurchase ? " · " : ""}
                        {d.minimumPurchase
                          ? `Min ${money(d.minimumPurchase)}`
                          : ""}
                        {!d.minimumQuantity && !d.minimumPurchase ? "None" : ""}
                      </td>
                      <td className="text-slate-500">
                        {d.usedCount} / {d.usageLimit ?? "∞"}
                      </td>
                      <td>
                        <Badge value={d.status} />
                      </td>
                      <td className="text-right">
                        <Link
                          href={`/discounts/${d.id}`}
                          aria-label={`View ${d.name}`}
                        >
                          <ArrowUpRight className="ml-auto size-4 text-slate-400" />
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

export function DiscountDetail({ id, user }: { id: string; user: User }) {
  const [confirm, setConfirm] = useState(false);
  const client = useQueryClient();
  const router = useRouter();
  const result = useQuery({
    queryKey: ["discounts", id],
    queryFn: ({ signal }) => api<Discount>(`/discounts/${id}`, { signal }),
  });
  const remove = useMutation({
    mutationFn: () => api(`/discounts/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["discounts"] });
      toast.success("Discount deleted.");
      router.push("/discounts");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const d = result.data;
  return (
    <>
      <Link
        href="/discounts"
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to discounts
      </Link>
      <Heading
        eyebrow="Discount details"
        title={d.name}
        description={
          d.type === "PERCENTAGE"
            ? `${d.value}% off`
            : `${money(d.value)} fixed discount`
        }
        action={
          user.role === "ADMIN" && (
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setConfirm(true)}>
                <Trash2 />
                Delete
              </Button>
              <Button asChild>
                <Link href={`/discounts/${id}/edit`}>
                  <Pencil />
                  Edit discount
                </Link>
              </Button>
            </div>
          )
        }
      />
      <section className="card p-6">
        <div className="mb-7 flex items-center justify-between">
          <h2 className="font-semibold">Discount configuration</h2>
          <Badge value={d.status} />
        </div>
        <dl className="grid gap-7 sm:grid-cols-3">
          <DetailValue label="Type">{human(d.type)}</DetailValue>
          <DetailValue label="Value">
            {d.type === "PERCENTAGE" ? `${d.value}%` : money(d.value)}
          </DetailValue>
          <DetailValue label="Code">{d.code || "Automatic"}</DetailValue>
          <DetailValue label="Scope">{human(d.scope)}</DetailValue>
          <DetailValue label="Target product">
            {d.productName || "All products"}
          </DetailValue>
          <DetailValue label="Target subscription">
            {d.subscriptionNumber || "All subscriptions"}
          </DetailValue>
          <DetailValue label="Minimum quantity">
            {d.minimumQuantity ? String(d.minimumQuantity) : "None"}
          </DetailValue>
          <DetailValue label="Minimum purchase">
            {d.minimumPurchase ? money(d.minimumPurchase) : "None"}
          </DetailValue>
          <DetailValue label="Usage count">
            {d.usedCount} / {d.usageLimit ?? "Unlimited"}
          </DetailValue>
          <DetailValue label="Valid from">
            {d.validFrom || "Immediate"}
          </DetailValue>
          <DetailValue label="Valid until">
            {d.validUntil || "No expiry"}
          </DetailValue>
          <div className="sm:col-span-3">
            <DetailValue label="Description">{d.description}</DetailValue>
          </div>
        </dl>
      </section>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title={`Delete ${d.name}?`}
        pending={remove.isPending}
        onConfirm={() => remove.mutate()}
      >
        This removes the discount rule. Invoices already generated with this
        discount may prevent deletion.
      </ConfirmDialog>
    </>
  );
}

export function DiscountEditor({ id }: { id?: string }) {
  const result = useQuery({
    queryKey: ["discounts", id],
    queryFn: ({ signal }) => api<Discount>(`/discounts/${id}`, { signal }),
    enabled: !!id,
  });
  if (id && result.isPending) return <Loading />;
  if (result.error) return <ErrorState error={result.error} />;
  return <DiscountForm key={id || "new"} initial={result.data} />;
}

function DiscountForm({ initial }: { initial?: Discount }) {
  const router = useRouter();
  const client = useQueryClient();
  const products = useQuery({
    queryKey: ["products", "discount-picker"],
    queryFn: ({ signal }) =>
      api<Page<Product>>("/products?size=100", { signal }),
  });
  const subscriptions = useQuery({
    queryKey: ["subscriptions", "discount-picker"],
    queryFn: ({ signal }) =>
      api<Page<Subscription>>("/subscriptions?size=100", { signal }),
  });
  const form = useForm<z.infer<typeof discountSchema>>({
    resolver: zodResolver(discountSchema) as never,
    defaultValues: initial
      ? {
          name: initial.name,
          code: initial.code || "",
          description: initial.description || "",
          type: initial.type,
          value: Number(initial.value),
          scope: initial.scope,
          productId: initial.productId || undefined,
          subscriptionId: initial.subscriptionId || undefined,
          minimumQuantity: initial.minimumQuantity ?? "",
          minimumPurchase:
            initial.minimumPurchase !== undefined &&
            initial.minimumPurchase !== null
              ? Number(initial.minimumPurchase)
              : "",
          validFrom: initial.validFrom || "",
          validUntil: initial.validUntil || "",
          usageLimit: initial.usageLimit ?? "",
          status: initial.status,
        }
      : {
          name: "",
          code: "",
          description: "",
          type: "PERCENTAGE",
          value: 10,
          scope: "SUBSCRIPTION",
          productId: undefined,
          subscriptionId: undefined,
          minimumQuantity: "",
          minimumPurchase: "",
          validFrom: "",
          validUntil: "",
          usageLimit: "",
          status: "ACTIVE",
        },
  });
  const e = form.formState.errors;
  const scope = form.watch("scope");
  async function save(values: z.infer<typeof discountSchema>) {
    try {
      const body = {
        name: values.name,
        code: values.code?.trim() || null,
        description: values.description?.trim() || null,
        type: values.type,
        value: values.value,
        scope: values.scope,
        productId: values.productId || null,
        subscriptionId: values.subscriptionId || null,
        minimumQuantity:
          values.minimumQuantity === "" || values.minimumQuantity === undefined
            ? null
            : Number(values.minimumQuantity),
        minimumPurchase:
          values.minimumPurchase === "" || values.minimumPurchase === undefined
            ? null
            : Number(values.minimumPurchase),
        validFrom: values.validFrom || null,
        validUntil: values.validUntil || null,
        usageLimit:
          values.usageLimit === "" || values.usageLimit === undefined
            ? null
            : Number(values.usageLimit),
        status: values.status,
      };
      const saved = await api<Discount>(
        initial ? `/discounts/${initial.id}` : "/discounts",
        { method: initial ? "PUT" : "POST", body },
      );
      await client.invalidateQueries({ queryKey: ["discounts"] });
      toast.success(initial ? "Discount updated." : "Discount created.");
      router.push(`/discounts/${saved.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }
  return (
    <>
      <Heading
        eyebrow="Financial rules"
        title={initial ? "Edit discount" : "Create discount"}
        description="Define percentage or fixed discounts with automatic threshold and usage tracking."
      />
      <form
        className="card max-w-4xl p-6 sm:p-8"
        onSubmit={form.handleSubmit(save)}
        noValidate
      >
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField label="Discount name" error={e.name?.message}>
            <input
              className="input"
              placeholder="e.g. Enterprise Volume 15%"
              {...form.register("name")}
            />
          </FormField>
          <FormField label="Code (optional)" error={e.code?.message}>
            <input
              className="input"
              placeholder="e.g. SEAT15"
              {...form.register("code")}
            />
          </FormField>
          <FormField label="Discount type">
            <select className="input" {...form.register("type")}>
              <option value="PERCENTAGE">Percentage (%)</option>
              <option value="FIXED">Fixed amount</option>
            </select>
          </FormField>
          <FormField label="Discount value" error={e.value?.message}>
            <input
              className="input"
              type="number"
              min="0.01"
              step="0.01"
              {...form.register("value", { valueAsNumber: true })}
            />
          </FormField>
          <FormField label="Applicability scope">
            <select className="input" {...form.register("scope")}>
              <option value="SUBSCRIPTION">Whole subscription</option>
              <option value="PRODUCT">Specific product</option>
            </select>
          </FormField>
          <FormField
            label={
              scope === "PRODUCT"
                ? "Target product (required)"
                : "Target product (optional)"
            }
            error={e.productId?.message}
          >
            <select
              className="input"
              value={form.watch("productId") || ""}
              onChange={(ev) =>
                form.setValue(
                  "productId",
                  ev.target.value ? Number(ev.target.value) : undefined,
                  { shouldValidate: true },
                )
              }
            >
              <option value="">All products</option>
              {products.data?.content.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField
            label="Target subscription (optional)"
            error={e.subscriptionId?.message}
          >
            <select
              className="input"
              value={form.watch("subscriptionId") || ""}
              onChange={(ev) =>
                form.setValue(
                  "subscriptionId",
                  ev.target.value ? Number(ev.target.value) : undefined,
                )
              }
            >
              <option value="">All qualifying subscriptions</option>
              {subscriptions.data?.content.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.number} ({s.customerName})
                </option>
              ))}
            </select>
          </FormField>
          <FormField
            label="Minimum quantity (optional)"
            error={e.minimumQuantity?.message}
          >
            <input
              className="input"
              type="number"
              min="1"
              step="1"
              placeholder="No minimum"
              {...form.register("minimumQuantity")}
            />
          </FormField>
          <FormField
            label="Minimum purchase amount (optional)"
            error={e.minimumPurchase?.message}
          >
            <input
              className="input"
              type="number"
              min="0"
              step="0.01"
              placeholder="No minimum"
              {...form.register("minimumPurchase")}
            />
          </FormField>
          <FormField
            label="Usage limit (optional)"
            error={e.usageLimit?.message}
          >
            <input
              className="input"
              type="number"
              min="1"
              step="1"
              placeholder="Unlimited"
              {...form.register("usageLimit")}
            />
          </FormField>
          <FormField label="Valid from" error={e.validFrom?.message}>
            <input
              className="input"
              type="date"
              {...form.register("validFrom")}
            />
          </FormField>
          <FormField label="Valid until" error={e.validUntil?.message}>
            <input
              className="input"
              type="date"
              {...form.register("validUntil")}
            />
          </FormField>
          <FormField label="Status">
            <select className="input" {...form.register("status")}>
              <option>ACTIVE</option>
              <option>INACTIVE</option>
            </select>
          </FormField>
          <div className="sm:col-span-2">
            <FormField label="Description">
              <textarea className="input" {...form.register("description")} />
            </FormField>
          </div>
        </div>
        <FormActions
          pending={form.formState.isSubmitting}
          cancel={initial ? `/discounts/${initial.id}` : "/discounts"}
          label={initial ? "Save discount" : "Create discount"}
        />
      </form>
    </>
  );
}
