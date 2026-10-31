"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  Ban,
  Check,
  FilePlus2,
  Pencil,
  Plus,
  Sparkles,
  Trash2,
} from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage, queryString } from "@/lib/api";
import { adjustment, human, money } from "@/lib/utils";
import {
  quotationStates,
  type Customer,
  type Page,
  type Plan,
  type Product,
  type Quotation,
  type QuotationTemplate,
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

const templateSchema = z.object({
  name: z.string().trim().min(2, "Template name is required.").max(160),
  description: z.string().max(2000).optional(),
  planId: z.number().min(1, "Select a subscription plan."),
  validityDays: z
    .number()
    .int()
    .min(1, "Validity must be at least 1 day.")
    .max(3650),
  paymentTerms: z.string().trim().min(1, "Payment terms are required.").max(1000),
  status: z.enum(["ACTIVE", "INACTIVE"]),
  lines: z
    .array(
      z.object({
        productId: z.number().min(1, "Select a product."),
        variantId: z.number().optional(),
        quantity: z.number().int().min(1, "Quantity must be at least 1."),
        unitPrice: z.string().optional(),
        taxRate: z.number().min(0).max(100),
      }),
    )
    .min(1, "Add at least one template line."),
});

export function QuotationList({
  user,
  initialTab,
}: {
  user: User;
  initialTab?: "quotations" | "templates";
}) {
  const [tab, setTab] = useState<"quotations" | "templates">(
    user.role === "CUSTOMER" ? "quotations" : initialTab || "templates",
  );
  const [q, setQ] = useState("");
  const [state, setState] = useState("");
  const [templateStatus, setTemplateStatus] = useState("");
  const [page, setPage] = useState(0);

  const templatesQuery = useQuery({
    queryKey: ["quotation-templates", { q, status: templateStatus, page }],
    queryFn: ({ signal }) =>
      api<Page<QuotationTemplate>>(
        `/quotation-templates${queryString({
          q,
          status: templateStatus || undefined,
          page,
          size: 10,
        })}`,
        { signal },
      ),
    enabled: user.role !== "CUSTOMER" && tab === "templates",
  });

  const quotationsQuery = useQuery({
    queryKey: ["quotations", { q, state, page }],
    queryFn: ({ signal }) =>
      api<Page<Quotation>>(
        `/quotations${queryString({
          q,
          state: state || undefined,
          page,
          size: 10,
        })}`,
        { signal },
      ),
    enabled: tab === "quotations" || user.role === "CUSTOMER",
  });

  return (
    <>
      <Heading
        eyebrow={
          user.role === "CUSTOMER"
            ? "Your quotations"
            : "Reusable templates & quotes"
        }
        title="Quotations"
        description={
          user.role === "CUSTOMER"
            ? "Review subscription quotations prepared for your account."
            : "Standardize recurring offers with reusable quotation templates and convert confirmed quotes into active subscriptions."
        }
        action={
          user.role !== "CUSTOMER" ? (
            <NewButton href="/quotation-templates/new">
              New quotation template
            </NewButton>
          ) : undefined
        }
      />

      {user.role !== "CUSTOMER" && (
        <div className="mb-5 flex gap-2">
          <Button
            type="button"
            variant={tab === "templates" ? "default" : "outline"}
            size="sm"
            onClick={() => {
              setTab("templates");
              setPage(0);
            }}
          >
            Quotation templates
          </Button>
          <Button
            type="button"
            variant={tab === "quotations" ? "default" : "outline"}
            size="sm"
            onClick={() => {
              setTab("quotations");
              setPage(0);
            }}
          >
            Customer quotations
          </Button>
        </div>
      )}

      {tab === "templates" && user.role !== "CUSTOMER" ? (
        <section className="card overflow-hidden">
          <div className="flex flex-wrap gap-3 border-b border-slate-100 p-5">
            <SearchField
              value={q}
              onChange={(v) => {
                setQ(v);
                setPage(0);
              }}
              placeholder="Search templates by name or plan…"
            />
            <select
              aria-label="Filter template status"
              className="input sm:ml-auto sm:!w-44"
              value={templateStatus}
              onChange={(e) => {
                setTemplateStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
          {templatesQuery.isPending ? (
            <div className="p-5">
              <Loading />
            </div>
          ) : templatesQuery.error ? (
            <div className="p-5">
              <ErrorState
                error={templatesQuery.error}
                retry={() => templatesQuery.refetch()}
              />
            </div>
          ) : !templatesQuery.data.content.length ? (
            <Empty
              title="No quotation templates yet"
              description="Create a reusable quotation template with default plan, products, quantities, and validity duration."
              action={
                <NewButton href="/quotation-templates/new">
                  Create template
                </NewButton>
              }
            />
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Template</th>
                      <th>Plan</th>
                      <th>Validity</th>
                      <th>Lines</th>
                      <th>Status</th>
                      <th>Estimated total</th>
                      <th>Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {templatesQuery.data.content.map((t) => (
                      <tr key={t.id}>
                        <td>
                          <Link href={`/quotation-templates/${t.id}`}>
                            {t.name}
                          </Link>
                        </td>
                        <td className="text-slate-500">
                          {t.planName}
                          {t.billingPeriod ? ` (${human(t.billingPeriod)})` : ""}
                        </td>
                        <td className="text-slate-500">
                          {t.validityDays} days
                        </td>
                        <td className="text-slate-500">
                          {t.lines.length} item{t.lines.length === 1 ? "" : "s"}
                        </td>
                        <td>
                          <Badge value={t.status} />
                        </td>
                        <td className="font-medium">
                          {money(t.estimatedTotal)}
                        </td>
                        <td>
                          <Link
                            href={`/quotation-templates/${t.id}`}
                            aria-label={`View ${t.name}`}
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
                totalPages={templatesQuery.data.totalPages}
                totalElements={templatesQuery.data.totalElements}
                onChange={setPage}
              />
            </>
          )}
        </section>
      ) : (
        <section className="card overflow-hidden">
          <div className="flex flex-wrap gap-3 border-b border-slate-100 p-5">
            <SearchField
              value={q}
              onChange={(v) => {
                setQ(v);
                setPage(0);
              }}
              placeholder="Search quotation number, customer, plan…"
            />
            <select
              aria-label="Filter quotation state"
              className="input sm:ml-auto sm:!w-44"
              value={state}
              onChange={(e) => {
                setState(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All states</option>
              {quotationStates.map((s) => (
                <option key={s} value={s}>
                  {human(s)}
                </option>
              ))}
            </select>
          </div>
          {quotationsQuery.isPending ? (
            <div className="p-5">
              <Loading />
            </div>
          ) : quotationsQuery.error ? (
            <div className="p-5">
              <ErrorState
                error={quotationsQuery.error}
                retry={() => quotationsQuery.refetch()}
              />
            </div>
          ) : !quotationsQuery.data.content.length ? (
            <Empty
              title="No quotations generated yet"
              description="Generate a quotation from a template to send pricing to a customer and convert it into a subscription."
            />
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Quotation</th>
                      {user.role !== "CUSTOMER" && <th>Customer</th>}
                      <th>Template / Plan</th>
                      <th>Valid until</th>
                      <th>State</th>
                      <th>Total</th>
                      <th>Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {quotationsQuery.data.content.map((qt) => (
                      <tr key={qt.id}>
                        <td>
                          <Link href={`/quotations/${qt.id}`}>
                            {qt.quotationNumber}
                          </Link>
                        </td>
                        {user.role !== "CUSTOMER" && (
                          <td className="text-slate-500">{qt.customerName}</td>
                        )}
                        <td className="text-slate-500">
                          {qt.templateName
                            ? `${qt.templateName} (${qt.planName})`
                            : qt.planName}
                        </td>
                        <td className="text-slate-500">
                          {qt.validUntil}
                          {qt.expired && qt.state !== "CONVERTED" && (
                            <span className="ml-2 rounded bg-rose-50 px-1.5 py-0.5 text-[10px] font-semibold text-rose-600">
                              Expired
                            </span>
                          )}
                        </td>
                        <td>
                          <Badge
                            value={
                              qt.expired &&
                              qt.state !== "CONVERTED" &&
                              qt.state !== "CANCELLED"
                                ? "EXPIRED"
                                : qt.state
                            }
                          />
                        </td>
                        <td className="font-medium">{money(qt.total)}</td>
                        <td>
                          <Link
                            href={`/quotations/${qt.id}`}
                            aria-label={`View ${qt.quotationNumber}`}
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
                totalPages={quotationsQuery.data.totalPages}
                totalElements={quotationsQuery.data.totalElements}
                onChange={setPage}
              />
            </>
          )}
        </section>
      )}
    </>
  );
}

export function QuotationTemplateDetail({
  id,
  user,
}: {
  id: string;
  user: User;
}) {
  const router = useRouter();
  const client = useQueryClient();
  const [genOpen, setGenOpen] = useState(false);
  const [customerId, setCustomerId] = useState(0);
  const [startDate, setStartDate] = useState(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [expiryDate, setExpiryDate] = useState(() => {
    const d = new Date();
    d.setFullYear(d.getFullYear() + 1);
    return d.toISOString().slice(0, 10);
  });
  const [notes, setNotes] = useState("");

  const result = useQuery({
    queryKey: ["quotation-templates", id],
    queryFn: ({ signal }) =>
      api<QuotationTemplate>(`/quotation-templates/${id}`, { signal }),
  });

  const customersQuery = useQuery({
    queryKey: ["customers", "active-picker"],
    queryFn: ({ signal }) =>
      api<Page<Customer>>(
        `/customers${queryString({ status: "ACTIVE", page: 0, size: 100 })}`,
        { signal },
      ),
    enabled: user.role !== "CUSTOMER",
  });

  const generateMutation = useMutation({
    mutationFn: () =>
      api<Quotation>(`/quotation-templates/${id}/generate`, {
        method: "POST",
        body: {
          customerId,
          startDate,
          expiryDate,
          notes: notes.trim() || undefined,
        },
      }),
    onSuccess: (created) => {
      client.invalidateQueries({ queryKey: ["quotations"] });
      setGenOpen(false);
      toast.success(`Generated quotation ${created.quotationNumber}.`);
      router.push(`/quotations/${created.id}`);
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const t = result.data;

  return (
    <>
      <Link
        href="/quotations"
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to quotations
      </Link>
      <Heading
        eyebrow="Quotation template"
        title={t.name}
        description={
          t.description ||
          `${t.planName} · Valid for ${t.validityDays} days from issue`
        }
        action={
          user.role !== "CUSTOMER" && (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" asChild>
                <Link href={`/quotation-templates/${t.id}/edit`}>
                  <Pencil />
                  Edit template
                </Link>
              </Button>
              {t.status === "ACTIVE" && (
                <Button onClick={() => setGenOpen(true)}>
                  <FilePlus2 />
                  Generate quotation
                </Button>
              )}
            </div>
          )
        }
      />

      <section className="card mb-6 p-6">
        <div className="mb-7 flex items-center justify-between">
          <h2 className="font-semibold">Template configuration</h2>
          <Badge value={t.status} />
        </div>
        <dl className="grid gap-7 sm:grid-cols-3">
          <DetailValue label="Template name">{t.name}</DetailValue>
          <DetailValue label="Subscription plan">
            {t.planName}
            {t.billingPeriod ? ` (${human(t.billingPeriod)})` : ""}
          </DetailValue>
          <DetailValue label="Validity duration">
            {t.validityDays} days
          </DetailValue>
          <DetailValue label="Default payment terms">
            {t.paymentTerms}
          </DetailValue>
          <DetailValue label="Estimated total">
            {money(t.estimatedTotal)}
          </DetailValue>
          <DetailValue label="Description">{t.description || "—"}</DetailValue>
        </dl>
      </section>

      <section className="card overflow-hidden">
        <div className="border-b border-slate-100 p-5">
          <h2 className="font-semibold">Default product lines</h2>
          <p className="mt-1 text-xs text-slate-400">
            Default quantities and unit prices used when generating a customer
            quotation.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Variant</th>
                <th>Default qty</th>
                <th>Default unit price</th>
                <th>Tax rate</th>
                <th>Line total</th>
              </tr>
            </thead>
            <tbody>
              {t.lines.map((l) => (
                <tr key={l.id}>
                  <td className="font-medium">{l.productName}</td>
                  <td className="text-slate-500">
                    {l.variantName || "Base product"}
                  </td>
                  <td>{l.quantity}</td>
                  <td>{money(l.unitPrice)}</td>
                  <td>{l.taxRate}%</td>
                  <td className="font-medium">{money(l.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <ConfirmDialog
        open={genOpen}
        onOpenChange={setGenOpen}
        title={`Generate quotation from "${t.name}"`}
        confirmLabel="Generate quotation"
        pendingLabel="Generating…"
        variant="default"
        pending={generateMutation.isPending}
        onConfirm={() => {
          if (!customerId) {
            toast.error("Please select a customer.");
            return;
          }
          generateMutation.mutate();
        }}
      >
        <div className="space-y-3">
          <FormField label="Customer">
            <select
              aria-label="Select customer"
              className="input"
              value={customerId || ""}
              onChange={(e) => setCustomerId(Number(e.target.value))}
            >
              <option value="">Select customer…</option>
              {customersQuery.data?.content.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.email})
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Subscription start date">
            <input
              aria-label="Subscription start date"
              type="date"
              className="input"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </FormField>
          <FormField label="Subscription expiry date">
            <input
              aria-label="Subscription expiry date"
              type="date"
              className="input"
              value={expiryDate}
              onChange={(e) => setExpiryDate(e.target.value)}
            />
          </FormField>
          <FormField label="Notes (optional)">
            <input
              aria-label="Quotation notes"
              className="input"
              placeholder="Special terms or remarks…"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </FormField>
        </div>
      </ConfirmDialog>
    </>
  );
}

export function QuotationTemplateEditor({ id }: { id?: string }) {
  const result = useQuery({
    queryKey: ["quotation-templates", id],
    queryFn: ({ signal }) =>
      api<QuotationTemplate>(`/quotation-templates/${id}`, { signal }),
    enabled: !!id,
  });
  if (id && result.isPending) return <Loading />;
  if (result.error) return <ErrorState error={result.error} />;
  return <TemplateForm key={id || "new"} initial={result.data} />;
}

function TemplateForm({ initial }: { initial?: QuotationTemplate }) {
  const router = useRouter();
  const client = useQueryClient();

  const plansQuery = useQuery({
    queryKey: ["plans", "template-picker"],
    queryFn: ({ signal }) =>
      api<Page<Plan>>(
        `/plans${queryString({ status: "ACTIVE", page: 0, size: 100 })}`,
        { signal },
      ),
  });

  const productsQuery = useQuery({
    queryKey: ["products", "template-picker"],
    queryFn: ({ signal }) =>
      api<Page<Product>>(
        `/products${queryString({ status: "ACTIVE", page: 0, size: 100 })}`,
        { signal },
      ),
  });

  const form = useForm<z.infer<typeof templateSchema>>({
    resolver: zodResolver(templateSchema),
    defaultValues: initial
      ? {
          name: initial.name,
          description: initial.description || "",
          planId: initial.planId,
          validityDays: initial.validityDays,
          paymentTerms: initial.paymentTerms,
          status: initial.status,
          lines: initial.lines.map((l) => ({
            productId: l.productId,
            variantId: l.variantId || undefined,
            quantity: Number(l.quantity),
            unitPrice: String(l.unitPrice),
            taxRate: Number(l.taxRate),
          })),
        }
      : {
          name: "",
          description: "",
          planId: 0,
          validityDays: 14,
          paymentTerms: "Net 15",
          status: "ACTIVE",
          lines: [{ productId: 0, quantity: 1, unitPrice: "", taxRate: 18 }],
        },
  });

  const lines = useFieldArray({ control: form.control, name: "lines" });
  const selectedPlanId = form.watch("planId");
  const selectedPlan = plansQuery.data?.content.find(
    (p) => p.id === Number(selectedPlanId),
  );
  const allProducts = productsQuery.data?.content || [];
  const planProducts = selectedPlan
    ? allProducts.filter((p) => selectedPlan.productIds.includes(p.id))
    : allProducts;
  const e = form.formState.errors;

  async function save(values: z.infer<typeof templateSchema>) {
    try {
      const payload = {
        name: values.name,
        description: values.description?.trim() || undefined,
        planId: values.planId,
        validityDays: values.validityDays,
        paymentTerms: values.paymentTerms,
        status: values.status,
        lines: values.lines.map((l) => ({
          productId: l.productId,
          variantId: l.variantId || undefined,
          quantity: l.quantity,
          unitPrice:
            l.unitPrice && l.unitPrice.trim() !== ""
              ? Number(l.unitPrice).toFixed(2)
              : undefined,
          taxRate: Number(l.taxRate).toFixed(2),
        })),
      };
      const saved = await api<QuotationTemplate>(
        initial
          ? `/quotation-templates/${initial.id}`
          : "/quotation-templates",
        {
          method: initial ? "PUT" : "POST",
          body: payload,
        },
      );
      await client.invalidateQueries({ queryKey: ["quotation-templates"] });
      toast.success("Quotation template saved.");
      router.push(`/quotation-templates/${saved.id}`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  return (
    <>
      <Heading
        eyebrow="Quotation templates"
        title={
          initial ? "Edit quotation template" : "Create quotation template"
        }
        description="Define a reusable subscription package with default products, quantities, prices, and quotation validity duration."
      />
      <form className="max-w-5xl" onSubmit={form.handleSubmit(save)} noValidate>
        <section className="card p-6">
          <h2 className="mb-6 font-semibold">Template settings</h2>
          <div className="grid gap-5 sm:grid-cols-2">
            <FormField label="Template name" error={e.name?.message}>
              <input
                className="input"
                placeholder="Enterprise Annual Bundle"
                {...form.register("name")}
              />
            </FormField>
            <FormField label="Subscription plan" error={e.planId?.message}>
              <select
                className="input"
                {...form.register("planId", { valueAsNumber: true })}
              >
                <option value="0">Select plan…</option>
                {plansQuery.data?.content.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({human(p.billingPeriod)})
                  </option>
                ))}
              </select>
            </FormField>
            <FormField
              label="Validity duration (days)"
              error={e.validityDays?.message}
            >
              <input
                type="number"
                min="1"
                max="3650"
                className="input"
                {...form.register("validityDays", { valueAsNumber: true })}
              />
            </FormField>
            <FormField
              label="Default payment terms"
              error={e.paymentTerms?.message}
            >
              <input className="input" {...form.register("paymentTerms")} />
            </FormField>
            <FormField label="Status" error={e.status?.message}>
              <select className="input" {...form.register("status")}>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </FormField>
            <FormField
              label="Description (optional)"
              error={e.description?.message}
            >
              <input className="input" {...form.register("description")} />
            </FormField>
          </div>
        </section>

        <section className="card mt-6 p-6">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h2 className="font-semibold">Template lines</h2>
              <p className="mt-1 text-xs text-slate-400">
                Leave default unit price blank to use the product catalog price.
              </p>
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() =>
                lines.append({
                  productId: 0,
                  quantity: 1,
                  unitPrice: "",
                  taxRate: 18,
                })
              }
            >
              <Plus />
              Add line
            </Button>
          </div>

          {lines.fields.map((field, index) => {
            const prodId = Number(form.watch(`lines.${index}.productId`));
            const prod = allProducts.find((p) => p.id === prodId);
            const lineErr = e.lines?.[index];
            return (
              <div
                key={field.id}
                className="mb-4 rounded-xl border border-slate-200 bg-slate-50/60 p-4"
              >
                <div className="mb-3 flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500">
                    LINE {index + 1}
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    disabled={lines.fields.length <= 1}
                    onClick={() => lines.remove(index)}
                  >
                    <Trash2 />
                    Remove
                  </Button>
                </div>
                <div className="grid gap-4 sm:grid-cols-5">
                  <div className="sm:col-span-2">
                    <FormField
                      label="Product"
                      error={lineErr?.productId?.message}
                    >
                      <select
                        className="input"
                        {...form.register(`lines.${index}.productId`, {
                          valueAsNumber: true,
                        })}
                      >
                        <option value="0">Select product…</option>
                        {planProducts.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name} ({money(p.salesPrice)})
                          </option>
                        ))}
                      </select>
                    </FormField>
                  </div>
                  <FormField label="Variant">
                    <select
                      className="input"
                      disabled={!prod?.variants.length}
                      value={form.watch(`lines.${index}.variantId`) || ""}
                      onChange={(ev) =>
                        form.setValue(
                          `lines.${index}.variantId`,
                          ev.target.value ? Number(ev.target.value) : undefined,
                        )
                      }
                    >
                      <option value="">Base product</option>
                      {prod?.variants.map((v) => (
                        <option key={v.id} value={v.id}>
                          {v.name} ({adjustment(v.priceAdjustment)})
                        </option>
                      ))}
                    </select>
                  </FormField>
                  <FormField
                    label="Default qty"
                    error={lineErr?.quantity?.message}
                  >
                    <input
                      type="number"
                      min="1"
                      className="input"
                      {...form.register(`lines.${index}.quantity`, {
                        valueAsNumber: true,
                      })}
                    />
                  </FormField>
                  <FormField
                    label="Default price"
                    error={lineErr?.unitPrice?.message}
                  >
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      className="input"
                      placeholder={prod ? String(prod.salesPrice) : "Catalog"}
                      {...form.register(`lines.${index}.unitPrice`)}
                    />
                  </FormField>
                </div>
              </div>
            );
          })}

          <FormActions
            pending={form.formState.isSubmitting}
            cancel={
              initial ? `/quotation-templates/${initial.id}` : "/quotations"
            }
            label="Save template"
          />
        </section>
      </form>
    </>
  );
}

export function QuotationDetail({ id, user }: { id: string; user: User }) {
  const router = useRouter();
  const client = useQueryClient();
  const [convertOpen, setConvertOpen] = useState(false);

  const result = useQuery({
    queryKey: ["quotations", id],
    queryFn: ({ signal }) => api<Quotation>(`/quotations/${id}`, { signal }),
  });

  const confirmMutation = useMutation({
    mutationFn: () =>
      api<Quotation>(`/quotations/${id}/confirm`, {
        method: "POST",
        body: {},
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["quotations"] });
      toast.success("Quotation confirmed.");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const cancelMutation = useMutation({
    mutationFn: () =>
      api<Quotation>(`/quotations/${id}/cancel`, {
        method: "POST",
        body: {},
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["quotations"] });
      toast.success("Quotation cancelled.");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const convertMutation = useMutation({
    mutationFn: () =>
      api<Subscription>(`/quotations/${id}/convert`, {
        method: "POST",
        body: { activateImmediately: true },
      }),
    onSuccess: (sub) => {
      client.invalidateQueries({ queryKey: ["quotations"] });
      client.invalidateQueries({ queryKey: ["subscriptions"] });
      setConvertOpen(false);
      toast.success(`Converted quotation into subscription ${sub.number}!`);
      router.push(`/subscriptions/${sub.id}`);
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const q = result.data;

  return (
    <>
      <Link
        href="/quotations"
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to quotations
      </Link>
      <Heading
        eyebrow="Quotation details"
        title={q.quotationNumber}
        description={`${q.customerName} · ${q.planName}`}
        action={
          <div className="flex flex-wrap gap-2">
            {q.convertedSubscriptionId && (
              <Button variant="outline" asChild>
                <Link href={`/subscriptions/${q.convertedSubscriptionId}`}>
                  View subscription ({q.convertedSubscriptionNumber})
                  <ArrowRight />
                </Link>
              </Button>
            )}
            {user.role !== "CUSTOMER" &&
              q.state !== "CONVERTED" &&
              q.state !== "CANCELLED" && (
                <>
                  <Button
                    variant="outline"
                    disabled={cancelMutation.isPending}
                    onClick={() => cancelMutation.mutate()}
                  >
                    <Ban />
                    Cancel
                  </Button>
                  {(q.state === "DRAFT" || q.state === "SENT") && (
                    <Button
                      variant="outline"
                      disabled={confirmMutation.isPending}
                      onClick={() => confirmMutation.mutate()}
                    >
                      <Check />
                      {confirmMutation.isPending
                        ? "Confirming…"
                        : "Confirm quotation"}
                    </Button>
                  )}
                  <Button
                    disabled={convertMutation.isPending}
                    onClick={() => setConvertOpen(true)}
                  >
                    <Sparkles />
                    Convert to subscription
                  </Button>
                </>
              )}
          </div>
        }
      />

      <section className="card mb-6 p-6">
        <div className="mb-7 flex items-center justify-between">
          <div>
            <h2 className="font-semibold">Quotation overview</h2>
            {q.templateName && (
              <p className="mt-1 text-xs text-slate-400">
                Generated from template{" "}
                <Link
                  href={`/quotation-templates/${q.templateId}`}
                  className="font-semibold text-violet-600"
                >
                  {q.templateName}
                </Link>
              </p>
            )}
          </div>
          <Badge
            value={
              q.expired && q.state !== "CONVERTED" && q.state !== "CANCELLED"
                ? "EXPIRED"
                : q.state
            }
          />
        </div>
        <dl className="grid gap-7 sm:grid-cols-3">
          <DetailValue label="Customer">{q.customerName}</DetailValue>
          <DetailValue label="Plan">
            {q.planName}
            {q.billingPeriod ? ` (${human(q.billingPeriod)})` : ""}
          </DetailValue>
          <DetailValue label="Issue date">{q.issueDate}</DetailValue>
          <DetailValue label="Valid until">{q.validUntil}</DetailValue>
          <DetailValue label="Subscription start">{q.startDate}</DetailValue>
          <DetailValue label="Subscription expiry">{q.expiryDate}</DetailValue>
          <DetailValue label="Payment terms">{q.paymentTerms}</DetailValue>
          <DetailValue label="Total amount">{money(q.total)}</DetailValue>
          {q.convertedSubscriptionNumber && (
            <DetailValue label="Converted subscription">
              <Link
                href={`/subscriptions/${q.convertedSubscriptionId}`}
                className="font-semibold text-violet-600 underline"
              >
                {q.convertedSubscriptionNumber}
              </Link>
            </DetailValue>
          )}
        </dl>
      </section>

      <section className="card overflow-hidden">
        <div className="border-b border-slate-100 p-5">
          <h2 className="font-semibold">Quotation lines</h2>
          <p className="mt-1 text-xs text-slate-400">
            Pricing snapshots are preserved when converting this quotation into
            a subscription.
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
                <th>Subtotal</th>
                <th>Tax</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {q.lines.map((l) => (
                <tr key={l.id}>
                  <td className="font-medium">{l.productName}</td>
                  <td className="text-slate-500">
                    {l.variantName || "Base product"}
                  </td>
                  <td>{l.quantity}</td>
                  <td>{money(l.unitPrice)}</td>
                  <td>{money(l.subtotal)}</td>
                  <td className="text-slate-500">
                    {money(l.taxAmount)} ({l.taxRate}%)
                  </td>
                  <td className="font-medium">{money(l.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="border-t border-slate-100 bg-slate-50/50 p-6">
          <div className="ml-auto max-w-xs space-y-2 text-sm">
            <div className="flex justify-between text-slate-500">
              <span>Subtotal</span>
              <span>{money(q.subtotal)}</span>
            </div>
            <div className="flex justify-between text-slate-500">
              <span>Tax</span>
              <span>{money(q.taxAmount)}</span>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-3 text-base font-semibold text-slate-900">
              <span>Quotation total</span>
              <span>{money(q.total)}</span>
            </div>
          </div>
        </div>
      </section>

      <ConfirmDialog
        open={convertOpen}
        onOpenChange={setConvertOpen}
        title={`Convert ${q.quotationNumber} to subscription?`}
        confirmLabel="Convert & activate"
        pendingLabel="Converting…"
        variant="default"
        pending={convertMutation.isPending}
        onConfirm={() => convertMutation.mutate()}
      >
        Converting this quotation creates an active subscription for{" "}
        <span className="font-semibold text-slate-800">{q.customerName}</span>{" "}
        with the exact unit prices and tax snapshots on this quote (
        {money(q.total)}). Duplicate conversions are automatically prevented.
      </ConfirmDialog>
    </>
  );
}
