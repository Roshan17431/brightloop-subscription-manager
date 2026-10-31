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
  ArrowUpRight,
  Check,
  Pencil,
  Plus,
  Trash2,
} from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage, queryString } from "@/lib/api";
import { money, moneySum, human, adjustment } from "@/lib/utils";
import type { Page, Plan, Product, User } from "@/lib/types";
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
import { planSchema, productSchema } from "./schemas";
export function CatalogList({
  kind,
  user,
}: {
  kind: "products" | "plans";
  user: User;
}) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: [kind, { q, status, page }],
    queryFn: ({ signal }) =>
      api<Page<Product | Plan>>(
        `/${kind}${queryString({ q, status, page, size: 10 })}`,
        { signal },
      ),
  });
  return (
    <>
      <Heading
        eyebrow="Your catalog"
        title={human(kind)}
        description={
          kind === "products"
            ? "The products and possibilities behind every subscription."
            : "Flexible plans, clearly defined. Build a foundation for lasting relationships."
        }
        action={
          user.role === "ADMIN" && (
            <NewButton href={`/${kind}/new`}>
              New {kind === "products" ? "product" : "plan"}
            </NewButton>
          )
        }
      />
      <section className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-5">
          <SearchField
            value={q}
            onChange={(value) => {
              setQ(value);
              setPage(0);
            }}
            placeholder={`Search ${kind}…`}
          />
          <select
            aria-label="Filter by status"
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
          <div className="p-6">
            <Loading />
          </div>
        ) : result.error ? (
          <div className="p-5">
            <ErrorState error={result.error} retry={() => result.refetch()} />
          </div>
        ) : !result.data.content.length ? (
          <Empty
            title={
              q || status ? "No matching records" : `Your ${kind} start here`
            }
            description={
              q || status
                ? "Try a different search or filter."
                : `Create your first ${kind === "products" ? "product and add its variants" : "plan and choose its products"}.`
            }
            action={
              user.role === "ADMIN" ? (
                <NewButton href={`/${kind}/new`}>
                  Create {kind === "products" ? "product" : "plan"}
                </NewButton>
              ) : undefined
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>{kind === "products" ? "Product" : "Plan"}</th>
                    <th>{kind === "products" ? "Type" : "Frequency"}</th>
                    <th>
                      {kind === "products" ? "Sales price" : "Plan price"}
                    </th>
                    <th>
                      {kind === "products" ? "Variants" : "Minimum quantity"}
                    </th>
                    <th>Status</th>
                    <th className="!text-right">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <Link
                          href={`/${kind}/${item.id}`}
                          className="flex items-center gap-3"
                        >
                          <span className="flex size-9 items-center justify-center rounded-lg bg-violet-50 text-xs font-semibold text-violet-600">
                            {item.name.slice(0, 2).toUpperCase()}
                          </span>
                          {item.name}
                        </Link>
                      </td>
                      <td className="text-slate-500">
                        {"type" in item ? item.type : human(item.billingPeriod)}
                      </td>
                      <td className="font-medium">
                        {money(
                          "salesPrice" in item ? item.salesPrice : item.price,
                        )}
                      </td>
                      <td className="text-slate-500">
                        {"variants" in item
                          ? item.variants.length
                          : item.minimumQuantity}
                      </td>
                      <td>
                        <Badge value={item.status} />
                      </td>
                      <td className="text-right">
                        <Link
                          aria-label={`View ${item.name}`}
                          href={`/${kind}/${item.id}`}
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
      <p className="mt-4 text-xs text-slate-400">
        Prices are shown in your business currency. No billing is generated from
        this catalog.
      </p>
    </>
  );
}
export function CatalogDetail({
  kind,
  id,
  user,
}: {
  kind: "products" | "plans";
  id: string;
  user: User;
}) {
  const [confirm, setConfirm] = useState(false);
  const client = useQueryClient();
  const router = useRouter();
  const result = useQuery({
    queryKey: [kind, id],
    queryFn: ({ signal }) => api<Product | Plan>(`/${kind}/${id}`, { signal }),
  });
  const remove = useMutation({
    mutationFn: () => api(`/${kind}/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: [kind] });
      toast.success("Record deleted.");
      router.push(`/${kind}`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const item = result.data;
  return (
    <>
      <Link
        href={`/${kind}`}
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to {kind}
      </Link>
      <Heading
        title={item.name}
        eyebrow={kind === "products" ? "Product details" : "Plan details"}
        description={`Record #${item.id}`}
        action={
          user.role === "ADMIN" && (
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setConfirm(true)}>
                <Trash2 />
                Delete
              </Button>
              <Button asChild>
                <Link href={`/${kind}/${id}/edit`}>
                  <Pencil />
                  Edit {kind === "products" ? "product" : "plan"}
                </Link>
              </Button>
            </div>
          )
        }
      />
      <div className="card p-6">
        <div className="mb-7 flex items-center justify-between">
          <h2 className="font-semibold">At a glance</h2>
          <Badge value={item.status} />
        </div>
        <dl className="grid gap-8 sm:grid-cols-3">
          {"type" in item ? (
            <>
              <DetailValue label="Product type">{item.type}</DetailValue>
              <DetailValue label="Sales price">
                {money(item.salesPrice)}
              </DetailValue>
              <DetailValue label="Cost price">
                {item.costPrice === null ? "Restricted" : money(item.costPrice)}
              </DetailValue>
              <div className="sm:col-span-3">
                <DetailValue label="Notes">{item.notes}</DetailValue>
              </div>
            </>
          ) : (
            <>
              <DetailValue label="Billing frequency">
                {human(item.billingPeriod)}
              </DetailValue>
              <DetailValue label="Plan price">{money(item.price)}</DetailValue>
              <DetailValue label="Minimum quantity">
                {item.minimumQuantity}
              </DetailValue>
              <DetailValue label="Available from">
                {item.availableFrom}
              </DetailValue>
              <DetailValue label="Available until">
                {item.availableUntil}
              </DetailValue>
              <DetailValue label="Associated products">
                {item.productIds?.length || 0}
              </DetailValue>
              <div className="sm:col-span-3">
                <DetailValue label="Description">
                  {item.description}
                </DetailValue>
              </div>
            </>
          )}
        </dl>
      </div>
      {"variants" in item ? (
        <section className="card mt-6 overflow-hidden">
          <div className="border-b border-slate-100 p-5">
            <h2 className="font-semibold">Product variants</h2>
            <p className="mt-1 text-xs text-slate-400">
              Adjustments are added to the base sales price.
            </p>
          </div>
          {item.variants.length ? (
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Variant</th>
                    <th>Adjustment</th>
                    <th>Unit price</th>
                  </tr>
                </thead>
                <tbody>
                  {item.variants.map((v, i) => (
                    <tr key={v.id || i}>
                      <td>{v.name}</td>
                      <td>{adjustment(v.priceAdjustment)}</td>
                      <td>{moneySum(item.salesPrice, v.priceAdjustment)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty
              title="No variants"
              description="This product uses its base sales price."
            />
          )}
        </section>
      ) : (
        <section className="card mt-6 p-6">
          <h2 className="mb-5 font-semibold">Plan rules</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            {(
              [
                "automaticClosing",
                "earlyClosingAllowed",
                "pausingAllowed",
                "renewalAllowed",
              ] as const
            ).map((key) => (
              <div
                key={key}
                className="flex items-center justify-between rounded-lg border border-slate-100 p-4 text-sm"
              >
                <span>{human(key.replace(/([A-Z])/g, " $1"))}</span>
                <span
                  className={item[key] ? "text-teal-600" : "text-slate-400"}
                >
                  {item[key] ? "Enabled" : "Disabled"}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title={`Delete ${item.name}?`}
        pending={remove.isPending}
        onConfirm={() => remove.mutate()}
      >
        This removes the record from your catalog. Records used by subscriptions
        may be protected by the server.
      </ConfirmDialog>
    </>
  );
}
export function ProductEditor({ id }: { id?: string }) {
  const result = useQuery({
    queryKey: ["products", id],
    queryFn: ({ signal }) => api<Product>(`/products/${id}`, { signal }),
    enabled: !!id,
  });
  if (id && result.isPending) return <Loading />;
  if (result.error) return <ErrorState error={result.error} />;
  return <ProductForm key={id || "new"} initial={result.data} />;
}
function ProductForm({ initial }: { initial?: Product }) {
  const router = useRouter();
  const client = useQueryClient();
  const form = useForm<z.infer<typeof productSchema>>({
    resolver: zodResolver(productSchema),
    defaultValues: initial
      ? {
          ...initial,
          salesPrice: Number(initial.salesPrice),
          costPrice: Number(initial.costPrice),
          notes: initial.notes || "",
          status: initial.status as "ACTIVE" | "INACTIVE",
          variants: initial.variants.map((v) => ({
            ...v,
            priceAdjustment: Number(v.priceAdjustment),
          })),
        }
      : {
          name: "",
          type: "Service",
          salesPrice: 0,
          costPrice: 0,
          notes: "",
          status: "ACTIVE",
          variants: [],
        },
  });
  const variants = useFieldArray({ control: form.control, name: "variants" });
  const e = form.formState.errors;
  const save = async (values: z.infer<typeof productSchema>) => {
    try {
      const saved = await api<Product>(
        initial ? `/products/${initial.id}` : "/products",
        { method: initial ? "PUT" : "POST", body: values },
      );
      await client.invalidateQueries({ queryKey: ["products"] });
      toast.success(initial ? "Product updated." : "Product created.");
      router.push(`/products/${saved.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };
  return (
    <>
      <Heading
        eyebrow="Your catalog"
        title={initial ? "Edit product" : "Create a product"}
        description="Define your product, then add the options that make it yours."
      />
      <form
        className="card max-w-4xl p-6 sm:p-8"
        onSubmit={form.handleSubmit(save)}
        noValidate
      >
        <h2 className="mb-6 font-semibold">Product information</h2>
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField label="Product name" error={e.name?.message}>
            <input className="input" {...form.register("name")} />
          </FormField>
          <FormField label="Product type" error={e.type?.message}>
            <input
              className="input"
              placeholder="e.g. Service, Software, Physical"
              {...form.register("type")}
            />
          </FormField>
          <FormField label="Sales price" error={e.salesPrice?.message}>
            <input
              className="input"
              type="number"
              min="0"
              step="0.01"
              {...form.register("salesPrice", { valueAsNumber: true })}
            />
          </FormField>
          <FormField label="Cost price" error={e.costPrice?.message}>
            <input
              className="input"
              type="number"
              min="0"
              step="0.01"
              {...form.register("costPrice", { valueAsNumber: true })}
            />
          </FormField>
          <FormField label="Status">
            <select className="input" {...form.register("status")}>
              <option>ACTIVE</option>
              <option>INACTIVE</option>
            </select>
          </FormField>
          <div className="sm:col-span-2">
            <FormField label="Notes">
              <textarea className="input" {...form.register("notes")} />
            </FormField>
          </div>
        </div>
        <div className="mb-5 mt-8 flex items-center justify-between border-t border-slate-100 pt-7">
          <div>
            <h2 className="font-semibold">Variants</h2>
            <p className="mt-1 text-xs text-slate-400">
              Price adjustments are added to the base sales price.
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => variants.append({ name: "", priceAdjustment: 0 })}
          >
            <Plus />
            Add variant
          </Button>
        </div>
        {variants.fields.map((variant, i) => (
          <div
            key={variant.id}
            className="mb-4 flex items-start gap-3 rounded-xl bg-slate-50 p-4"
          >
            <div className="grid flex-1 gap-4 sm:grid-cols-2">
              <FormField
                label="Variant name"
                error={e.variants?.[i]?.name?.message}
              >
                <input
                  className="input"
                  {...form.register(`variants.${i}.name`)}
                />
              </FormField>
              <FormField
                label="Price adjustment"
                error={e.variants?.[i]?.priceAdjustment?.message}
              >
                <input
                  className="input"
                  type="number"
                  step="0.01"
                  {...form.register(`variants.${i}.priceAdjustment`, {
                    valueAsNumber: true,
                  })}
                />
              </FormField>
            </div>
            <Button
              variant="ghost"
              size="icon"
              type="button"
              className="mt-6"
              aria-label={`Remove variant ${i + 1}`}
              onClick={() => variants.remove(i)}
            >
              <Trash2 />
            </Button>
          </div>
        ))}
        <FormActions
          pending={form.formState.isSubmitting}
          cancel={initial ? `/products/${initial.id}` : "/products"}
          label={initial ? "Save product" : "Create product"}
        />
      </form>
    </>
  );
}
export function PlanEditor({ id }: { id?: string }) {
  const result = useQuery({
    queryKey: ["plans", id],
    queryFn: ({ signal }) => api<Plan>(`/plans/${id}`, { signal }),
    enabled: !!id,
  });
  if (id && result.isPending) return <Loading />;
  if (result.error) return <ErrorState error={result.error} />;
  return <PlanForm key={id || "new"} initial={result.data} />;
}
function PlanForm({ initial }: { initial?: Plan }) {
  const router = useRouter();
  const client = useQueryClient();
  const [productPage, setProductPage] = useState(0);
  const [productSearch, setProductSearch] = useState("");
  const products = useQuery({
    queryKey: ["products", "selector", productPage, productSearch],
    queryFn: ({ signal }) =>
      api<Page<Product>>(
        `/products${queryString({ q: productSearch, page: productPage, size: 20 })}`,
        { signal },
      ),
  });
  const form = useForm<z.infer<typeof planSchema>>({
    resolver: zodResolver(planSchema),
    defaultValues: initial
      ? {
          ...initial,
          price: Number(initial.price),
          description: initial.description || "",
          productIds: initial.productIds || [],
          status: initial.status as "ACTIVE" | "INACTIVE",
        }
      : {
          name: "",
          description: "",
          billingPeriod: "MONTHLY",
          price: 0,
          minimumQuantity: 1,
          availableFrom: new Date().toISOString().slice(0, 10),
          availableUntil: "",
          automaticClosing: false,
          earlyClosingAllowed: false,
          pausingAllowed: false,
          renewalAllowed: true,
          status: "ACTIVE",
          productIds: [],
        },
  });
  const e = form.formState.errors;
  const selected = form.watch("productIds");
  const save = async (values: z.infer<typeof planSchema>) => {
    try {
      const saved = await api<Plan>(
        initial ? `/plans/${initial.id}` : "/plans",
        { method: initial ? "PUT" : "POST", body: values },
      );
      await client.invalidateQueries({ queryKey: ["plans"] });
      toast.success("Plan saved.");
      router.push(`/plans/${saved.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };
  return (
    <>
      <Heading
        eyebrow="Your catalog"
        title={initial ? "Edit plan" : "Create a plan"}
        description="Set the terms, availability, and products for your subscription plan."
      />
      <form
        className="card max-w-4xl p-6 sm:p-8"
        onSubmit={form.handleSubmit(save)}
        noValidate
      >
        <h2 className="mb-6 font-semibold">Plan information</h2>
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField label="Plan name" error={e.name?.message}>
            <input className="input" {...form.register("name")} />
          </FormField>
          <FormField label="Billing frequency">
            <select className="input" {...form.register("billingPeriod")}>
              {["DAILY", "WEEKLY", "MONTHLY", "YEARLY"].map((v) => (
                <option key={v} value={v}>
                  {human(v)}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Plan price" error={e.price?.message}>
            <input
              className="input"
              type="number"
              step="0.01"
              min="0"
              {...form.register("price", { valueAsNumber: true })}
            />
          </FormField>
          <FormField
            label="Minimum quantity"
            error={e.minimumQuantity?.message}
          >
            <input
              className="input"
              type="number"
              min="1"
              step="1"
              {...form.register("minimumQuantity", { valueAsNumber: true })}
            />
          </FormField>
          <FormField label="Available from" error={e.availableFrom?.message}>
            <input
              className="input"
              type="date"
              {...form.register("availableFrom")}
            />
          </FormField>
          <FormField label="Available until" error={e.availableUntil?.message}>
            <input
              className="input"
              type="date"
              {...form.register("availableUntil")}
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
        <h2 className="mb-4 mt-8 border-t border-slate-100 pt-7 font-semibold">
          Plan rules
        </h2>
        <div className="grid gap-4 sm:grid-cols-2">
          {(
            [
              "automaticClosing",
              "earlyClosingAllowed",
              "pausingAllowed",
              "renewalAllowed",
            ] as const
          ).map((key) => (
            <label
              key={key}
              className="flex items-center gap-3 rounded-lg border border-slate-200 p-4 text-sm"
            >
              <input
                type="checkbox"
                className="size-4 accent-violet-600"
                {...form.register(key)}
              />
              {human(key.replace(/([A-Z])/g, " $1"))}
            </label>
          ))}
        </div>
        <h2 className="mb-4 mt-8 border-t border-slate-100 pt-7 font-semibold">
          Associated products{" "}
          <span className="text-sm font-normal text-slate-400">
            ({selected.length} selected)
          </span>
        </h2>
        <SearchField
          value={productSearch}
          onChange={(v) => {
            setProductSearch(v);
            setProductPage(0);
          }}
          placeholder="Find products…"
        />
        {products.isPending ? (
          <p className="mt-4 text-sm text-slate-400">Loading products…</p>
        ) : products.error ? (
          <ErrorState error={products.error} />
        ) : (
          <>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              {products.data.content.map((p) => (
                <label
                  key={p.id}
                  className="flex items-center gap-3 rounded-lg border border-slate-200 p-4 text-sm"
                >
                  <input
                    type="checkbox"
                    className="size-4 accent-violet-600"
                    checked={selected.includes(p.id)}
                    onChange={(ev) =>
                      form.setValue(
                        "productIds",
                        ev.target.checked
                          ? [...selected, p.id]
                          : selected.filter((id) => id !== p.id),
                        { shouldValidate: true },
                      )
                    }
                  />
                  {p.name}
                  <span className="ml-auto text-xs text-slate-400">
                    {money(p.salesPrice)}
                  </span>
                </label>
              ))}
            </div>
            {!products.data.content.length && (
              <p className="mt-4 text-sm text-slate-500">
                No products found. Create products before configuring a plan.
              </p>
            )}
            <Pagination
              page={productPage}
              totalPages={products.data.totalPages}
              totalElements={products.data.totalElements}
              onChange={setProductPage}
            />
          </>
        )}
        {e.productIds && (
          <p role="alert" className="mt-2 text-xs text-rose-600">
            {e.productIds.message}
          </p>
        )}
        <FormActions
          pending={form.formState.isSubmitting}
          cancel={initial ? `/plans/${initial.id}` : "/plans"}
          label={initial ? "Save plan" : "Create plan"}
        />
      </form>
    </>
  );
}
