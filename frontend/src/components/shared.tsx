"use client";
import Link from "next/link";
import { cloneElement, isValidElement, useId, type ReactElement } from "react";
import {
  ArrowLeft,
  ArrowRight,
  CircleAlert,
  Inbox,
  LoaderCircle,
  Plus,
  Search,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { human } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
export function Heading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-7 flex min-w-0 flex-wrap items-end justify-between gap-4">
      <div className="min-w-0 max-w-full flex-1 basis-64">
        <p className="mb-2 text-xs font-semibold uppercase tracking-[.15em] text-violet-600">
          {eyebrow || "Workspace"}
        </p>
        <h1 className="text-3xl font-semibold tracking-tight text-slate-900 [overflow-wrap:anywhere]">
          {title}
        </h1>
        {description && (
          <p className="mt-2 text-sm leading-6 text-slate-500 [overflow-wrap:anywhere]">
            {description}
          </p>
        )}
      </div>
      {action}
    </div>
  );
}
export function NewButton({
  href,
  children,
}: {
  href: string;
  children: React.ReactNode;
}) {
  return (
    <Button asChild>
      <Link href={href}>
        <Plus />
        {children}
      </Link>
    </Button>
  );
}
export function Badge({ value }: { value: string }) {
  return (
    <span
      className={`badge ${["ACTIVE", "CONFIRMED"].includes(value) ? "badge-green" : ["INACTIVE", "CLOSED"].includes(value) ? "badge-gray" : "badge-violet"}`}
    >
      <span className="size-1.5 rounded-full bg-current" />
      {human(value)}
    </span>
  );
}
export function Loading() {
  return (
    <div className="space-y-4" role="status" aria-label="Loading records">
      <div className="h-9 w-52 animate-pulse rounded-lg bg-slate-200/70" />
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="h-24 animate-pulse rounded-xl border border-slate-100 bg-white"
        />
      ))}
      <span className="sr-only">Loading…</span>
    </div>
  );
}
export function ErrorState({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  return (
    <div
      role="alert"
      className="rounded-xl border border-rose-100 bg-rose-50 p-6"
    >
      <CircleAlert className="mb-3 text-rose-500" />
      <h2 className="font-semibold text-slate-900">
        We couldn’t load this information
      </h2>
      <p className="mt-1 text-sm text-slate-600">{errorMessage(error)}</p>
      {retry && (
        <Button variant="outline" className="mt-4" onClick={retry}>
          Try again
        </Button>
      )}
    </div>
  );
}
export function Empty({
  title = "No records yet",
  description = "Records will appear here when you add them.",
  action,
}: {
  title?: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex min-h-64 flex-col items-center justify-center px-5 py-12 text-center">
      <div className="mb-4 rounded-2xl bg-violet-50 p-4">
        <Inbox className="size-6 text-violet-500" />
      </div>
      <h3 className="font-semibold">{title}</h3>
      <p className="mb-5 mt-2 max-w-sm text-sm leading-6 text-slate-500">
        {description}
      </p>
      {action}
    </div>
  );
}
export function SearchField({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  return (
    <div className="relative min-w-48 flex-1 sm:max-w-sm">
      <Search className="absolute left-3 top-3 size-4 text-slate-400" />
      <input
        className="input pl-9"
        aria-label={placeholder || "Search records"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder || "Search records…"}
      />
    </div>
  );
}
export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-5 py-4 text-xs text-slate-500">
      <span>
        {totalElements} record{totalElements === 1 ? "" : "s"} · Page {page + 1}{" "}
        of {Math.max(totalPages, 1)}
      </span>
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          <ArrowLeft />
          Previous
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
        >
          Next
          <ArrowRight />
        </Button>
      </div>
    </div>
  );
}
export function FormField({
  label,
  error,
  children,
  hint,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
  hint?: string;
}) {
  const id = useId();
  const describedBy =
    [hint ? `${id}-hint` : "", error ? `${id}-error` : ""]
      .filter(Boolean)
      .join(" ") || undefined;
  const control = isValidElement(children)
    ? cloneElement(children as ReactElement<Record<string, unknown>>, {
        id,
        "aria-describedby": describedBy,
        "aria-invalid": error ? true : undefined,
      })
    : children;
  return (
    <div>
      <label
        htmlFor={id}
        className="mb-2 block text-sm font-medium text-slate-700"
      >
        {label}
      </label>
      {control}
      {hint && (
        <span id={`${id}-hint`} className="mt-1.5 block text-xs text-slate-400">
          {hint}
        </span>
      )}
      {error && (
        <span
          id={`${id}-error`}
          role="alert"
          className="mt-1.5 block text-xs text-rose-600"
        >
          {error}
        </span>
      )}
    </div>
  );
}

export function FormActions({
  pending,
  cancel = "/dashboard",
  label = "Save changes",
}: {
  pending: boolean;
  cancel?: string;
  label?: string;
}) {
  return (
    <div className="mt-7 flex justify-end gap-3 border-t border-slate-100 pt-5">
      <Button type="button" variant="outline" asChild>
        <Link href={cancel}>Cancel</Link>
      </Button>
      <Button disabled={pending} type="submit">
        {pending && <LoaderCircle className="animate-spin" />}
        {pending ? "Saving…" : label}
      </Button>
    </div>
  );
}
export function DetailValue({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <dt className="text-xs font-medium text-slate-400">{label}</dt>
      <dd className="mt-2 text-sm font-medium text-slate-800 [overflow-wrap:anywhere]">
        {children || "—"}
      </dd>
    </div>
  );
}
