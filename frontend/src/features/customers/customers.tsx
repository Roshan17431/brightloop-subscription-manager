"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  ArrowLeft,
  ArrowUpRight,
  Mail,
  Pencil,
  Plus,
  UserRound,
} from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage, queryString } from "@/lib/api";
import type { Customer, Page, Role, User } from "@/lib/types";
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
import { registerSchema } from "@/features/auth/schemas";
export const customerSchema = z.object({
  name: z.string().trim().min(1, "Enter a name."),
  email: z.email("Enter a valid email."),
  phone: z.string(),
  companyName: z.string(),
  billingAddress: z.string(),
  status: z.enum(["ACTIVE", "INACTIVE"]),
});
export function CustomerList() {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ["customers", { q, status, page }],
    queryFn: ({ signal }) =>
      api<Page<Customer>>(
        `/customers${queryString({ q, status, page, size: 10 })}`,
        { signal },
      ),
  });
  return (
    <>
      <Heading
        eyebrow="Relationships"
        title="Customers"
        description="The people at the heart of your business. Keep every detail connected."
        action={<NewButton href="/customers/new">New customer</NewButton>}
      />
      <div className="card overflow-hidden">
        <div className="flex flex-wrap gap-3 border-b border-slate-100 p-5">
          <SearchField
            value={q}
            onChange={(v) => {
              setQ(v);
              setPage(0);
            }}
            placeholder="Search name, email or company…"
          />
          <select
            aria-label="Filter customer status"
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
          <ErrorState error={result.error} retry={() => result.refetch()} />
        ) : !result.data.content.length ? (
          <Empty
            title="No customers found"
            description={
              q || status
                ? "Try another search or filter."
                : "Create a customer record to start building your subscription relationships."
            }
            action={
              <NewButton href="/customers/new">Create customer</NewButton>
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Customer</th>
                    <th>Email</th>
                    <th>Company</th>
                    <th>Status</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <Link
                          href={`/customers/${c.id}`}
                          className="flex items-center gap-3"
                        >
                          <span className="flex size-9 items-center justify-center rounded-full bg-teal-50 text-xs font-semibold text-teal-700">
                            {c.name.slice(0, 2).toUpperCase()}
                          </span>
                          {c.name}
                        </Link>
                      </td>
                      <td className="text-slate-500">{c.email}</td>
                      <td className="text-slate-500">{c.companyName || "—"}</td>
                      <td>
                        <Badge value={c.status} />
                      </td>
                      <td>
                        <Link
                          href={`/customers/${c.id}`}
                          aria-label={`View ${c.name}`}
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
      </div>
    </>
  );
}
export function CustomerDetail({ id }: { id: string }) {
  const result = useQuery({
    queryKey: ["customers", id],
    queryFn: ({ signal }) => api<Customer>(`/customers/${id}`, { signal }),
  });
  if (result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  const c = result.data;
  return (
    <>
      <Link
        href="/customers"
        className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500"
      >
        <ArrowLeft size={14} />
        Back to customers
      </Link>
      <Heading
        eyebrow="Customer details"
        title={c.name}
        description={`Customer #${c.id}`}
        action={
          <Button asChild>
            <Link href={`/customers/${id}/edit`}>
              <Pencil />
              Edit customer
            </Link>
          </Button>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_2fr]">
        <div className="card p-7 text-center">
          <div className="mx-auto flex size-20 items-center justify-center rounded-full bg-teal-50 text-2xl font-semibold text-teal-700">
            {c.name.slice(0, 2).toUpperCase()}
          </div>
          <h2 className="mb-2 mt-4 text-lg font-semibold">{c.name}</h2>
          <p className="mb-4 text-sm text-slate-500">
            {c.companyName || "Individual customer"}
          </p>
          <Badge value={c.status} />
        </div>
        <div className="card p-7">
          <h2 className="mb-7 font-semibold">Contact & account</h2>
          <dl className="grid gap-8 sm:grid-cols-2">
            <DetailValue label="Email address">{c.email}</DetailValue>
            <DetailValue label="Phone">{c.phone}</DetailValue>
            <DetailValue label="Company">{c.companyName}</DetailValue>
            <DetailValue label="Linked account">
              {c.userId ? `User #${c.userId}` : "No linked login"}
            </DetailValue>
            <div className="sm:col-span-2">
              <DetailValue label="Billing address">
                {c.billingAddress}
              </DetailValue>
            </div>
            <DetailValue label="Created">
              {c.createdAt ? new Date(c.createdAt).toLocaleDateString() : "—"}
            </DetailValue>
            <DetailValue label="Last updated">
              {c.updatedAt ? new Date(c.updatedAt).toLocaleDateString() : "—"}
            </DetailValue>
          </dl>
        </div>
      </div>
    </>
  );
}
export function CustomerEditor({
  id,
  profile,
  user,
}: {
  id?: string;
  profile?: boolean;
  user?: User;
}) {
  const path = profile ? "/customers/me" : `/customers/${id}`;
  const result = useQuery({
    queryKey: ["customers", profile ? "me" : id],
    queryFn: ({ signal }) => api<Customer>(path, { signal }),
    enabled: !!id || !!profile,
  });
  if ((id || profile) && result.isPending) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={() => result.refetch()} />;
  return (
    <CustomerForm
      key={id || "me"}
      initial={result.data}
      profile={profile}
      user={user}
    />
  );
}
function CustomerForm({
  initial,
  profile,
  user,
}: {
  initial?: Customer;
  profile?: boolean;
  user?: User;
}) {
  const router = useRouter();
  const client = useQueryClient();
  const form = useForm<z.infer<typeof customerSchema>>({
    resolver: zodResolver(customerSchema),
    defaultValues: {
      name: initial?.name || "",
      email: initial?.email || "",
      phone: initial?.phone || "",
      companyName: initial?.companyName || "",
      billingAddress: initial?.billingAddress || "",
      status: (initial?.status as "ACTIVE" | "INACTIVE") || "ACTIVE",
    },
  });
  const e = form.formState.errors;
  async function save(values: z.infer<typeof customerSchema>) {
    try {
      const saved = await api<Customer>(
        profile
          ? "/customers/me"
          : initial
            ? `/customers/${initial.id}`
            : "/customers",
        { method: initial || profile ? "PUT" : "POST", body: values },
      );
      await client.invalidateQueries({ queryKey: ["customers"] });
      await client.invalidateQueries({ queryKey: ["me"] });
      toast.success(profile ? "Profile updated." : "Customer saved.");
      if (!profile) router.push(`/customers/${saved.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }
  return (
    <>
      <Heading
        eyebrow={profile ? "Your account" : "Relationships"}
        title={
          profile
            ? "My profile"
            : initial
              ? "Edit customer"
              : "Create a customer"
        }
        description={
          profile
            ? "Keep your contact and billing information up to date."
            : "Good relationships start with the right details."
        }
      />
      <form
        className="card max-w-3xl p-6 sm:p-8"
        onSubmit={form.handleSubmit(save)}
        noValidate
      >
        <div className="mb-7 flex items-center gap-4 border-b border-slate-100 pb-6">
          <div className="flex size-12 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
            <UserRound />
          </div>
          <div>
            <h2 className="font-semibold">
              {profile ? user?.name : "Customer information"}
            </h2>
            <p className="mt-1 text-xs text-slate-400">
              {profile
                ? user?.email
                : "Contact details and account preferences"}
            </p>
          </div>
          {profile && (
            <span className="ml-auto">
              <Badge value={user?.role || "CUSTOMER"} />
            </span>
          )}
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField label="Full name" error={e.name?.message}>
            <input
              className="input"
              autoComplete="name"
              {...form.register("name")}
            />
          </FormField>
          <FormField label="Email address" error={e.email?.message}>
            <input
              className="input"
              type="email"
              autoComplete="email"
              {...form.register("email")}
            />
          </FormField>
          <FormField label="Phone">
            <input
              className="input"
              autoComplete="tel"
              {...form.register("phone")}
            />
          </FormField>
          <FormField label="Company name">
            <input
              className="input"
              autoComplete="organization"
              {...form.register("companyName")}
            />
          </FormField>
          <div className="sm:col-span-2">
            <FormField label="Billing address">
              <textarea
                className="input"
                autoComplete="street-address"
                {...form.register("billingAddress")}
              />
            </FormField>
          </div>
          {!profile && (
            <FormField label="Account status">
              <select className="input" {...form.register("status")}>
                <option>ACTIVE</option>
                <option>INACTIVE</option>
              </select>
            </FormField>
          )}
        </div>
        <FormActions
          pending={form.formState.isSubmitting}
          cancel={
            profile
              ? "/dashboard"
              : initial
                ? `/customers/${initial.id}`
                : "/customers"
          }
          label={
            profile
              ? "Update profile"
              : initial
                ? "Save customer"
                : "Create customer"
          }
        />
      </form>
    </>
  );
}
export function StaffProfile({ user }: { user: User }) {
  return (
    <>
      <Heading
        eyebrow="Your account"
        title="My profile"
        description="Your account details and workspace access."
      />
      <div className="card max-w-3xl p-7">
        <div className="mb-7 flex items-center gap-4 border-b border-slate-100 pb-6">
          <span className="flex size-14 items-center justify-center rounded-xl bg-violet-50 text-lg font-semibold text-violet-600">
            {user.name.slice(0, 2).toUpperCase()}
          </span>
          <div>
            <h2 className="text-lg font-semibold">{user.name}</h2>
            <p className="mt-1 text-sm text-slate-500">{user.email}</p>
          </div>
        </div>
        <dl className="grid gap-7 sm:grid-cols-2">
          <DetailValue label="Account ID">#{user.id}</DetailValue>
          <DetailValue label="Workspace role">
            <Badge value={user.role} />
          </DetailValue>
        </dl>
        <div className="mt-7 border-t border-slate-100 pt-5">
          <Button variant="outline" asChild>
            <Link href="/forgot-password">Reset my password</Link>
          </Button>
        </div>
      </div>
    </>
  );
}
export function Team() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const client = useQueryClient();
  const result = useQuery({
    queryKey: ["users", { q, page }],
    queryFn: ({ signal }) =>
      api<Page<User>>(`/users${queryString({ q, page, size: 10 })}`, {
        signal,
      }),
  });
  const form = useForm<z.infer<typeof registerSchema>>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: "", email: "", password: "" },
  });
  const role = useMutation({
    mutationFn: ({ id, role }: { id: number; role: Role }) =>
      api(`/users/${id}/role`, { method: "PATCH", body: { role } }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["users"] });
      client.invalidateQueries({ queryKey: ["me"] });
      toast.success("Role updated.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  async function create(values: z.infer<typeof registerSchema>) {
    try {
      await api("/staff", { method: "POST", body: values });
      await client.invalidateQueries({ queryKey: ["users"] });
      form.reset();
      setCreating(false);
      toast.success("Staff account created.");
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }
  return (
    <>
      <Heading
        eyebrow="Administration"
        title="Team members"
        description="Give your people the access they need to keep things moving."
        action={
          <Button onClick={() => setCreating(!creating)}>
            <Plus />
            Create staff account
          </Button>
        }
      />
      {creating && (
        <form
          onSubmit={form.handleSubmit(create)}
          className="card mb-6 p-6"
          noValidate
        >
          <h2 className="mb-5 font-semibold">New staff member</h2>
          <div className="grid gap-5 md:grid-cols-3">
            <FormField
              label="Full name"
              error={form.formState.errors.name?.message}
            >
              <input className="input" {...form.register("name")} />
            </FormField>
            <FormField
              label="Email"
              error={form.formState.errors.email?.message}
            >
              <input
                className="input"
                type="email"
                {...form.register("email")}
              />
            </FormField>
            <FormField
              label="Temporary password"
              error={form.formState.errors.password?.message}
              hint="9+ characters, uppercase, lowercase, special character."
            >
              <input
                className="input"
                type="password"
                autoComplete="new-password"
                {...form.register("password")}
              />
            </FormField>
          </div>
          <div className="mt-5 flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setCreating(false)}
            >
              Cancel
            </Button>
            <Button disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? "Creating…" : "Create staff"}
            </Button>
          </div>
        </form>
      )}
      <div className="card overflow-hidden">
        <div className="border-b border-slate-100 p-5">
          <SearchField
            value={q}
            onChange={(v) => {
              setQ(v);
              setPage(0);
            }}
            placeholder="Search team members…"
          />
        </div>
        {result.isPending ? (
          <div className="p-5">
            <Loading />
          </div>
        ) : result.error ? (
          <ErrorState error={result.error} />
        ) : !result.data.content.length ? (
          <Empty title="No users found" />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Current role</th>
                    <th>Change role</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((u) => (
                    <tr key={u.id}>
                      <td className="font-medium">{u.name}</td>
                      <td className="text-slate-500">{u.email}</td>
                      <td>
                        <Badge value={u.role} />
                      </td>
                      <td>
                        <select
                          className="input !w-36"
                          aria-label={`Role for ${u.name}`}
                          value={u.role}
                          disabled={role.isPending}
                          onChange={(e) =>
                            role.mutate({
                              id: u.id,
                              role: e.target.value as Role,
                            })
                          }
                        >
                          <option>ADMIN</option>
                          <option>STAFF</option>
                          <option>CUSTOMER</option>
                        </select>
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
      </div>
    </>
  );
}
