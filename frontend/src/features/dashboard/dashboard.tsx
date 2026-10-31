"use client";
import Link from "next/link";
import dynamic from "next/dynamic";
import { useQueries, useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  ArrowUpRight,
  Boxes,
  ChevronRight,
  CreditCard,
  Package,
  Users,
  Workflow,
} from "lucide-react";
import { api } from "@/lib/api";
import { human, money } from "@/lib/utils";
import { states, type Page, type Subscription, type User } from "@/lib/types";
import {
  Badge,
  Empty,
  ErrorState,
  Heading,
  NewButton,
} from "@/components/shared";
const StateChart = dynamic(() => import("./state-chart"), {
  ssr: false,
  loading: () => <div className="h-56 animate-pulse rounded-lg bg-slate-50" />,
});
export function Dashboard({ user }: { user: User }) {
  const endpoints =
    user.role === "CUSTOMER"
      ? ["subscriptions"]
      : ["subscriptions", "customers", "products", "plans"];
  const stats = useQueries({
    queries: endpoints.map((endpoint) => ({
      queryKey: [endpoint, "count"],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        api<Page<unknown>>(`/${endpoint}?size=1`, { signal }),
    })),
  });
  const breakdown = useQueries({
    queries: states.map((state) => ({
      queryKey: ["subscriptions", "count", state],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        api<Page<Subscription>>(`/subscriptions?state=${state}&size=1`, {
          signal,
        }),
    })),
  });
  const recent = useQuery({
    queryKey: ["subscriptions", "recent"],
    queryFn: ({ signal }) =>
      api<Page<Subscription>>("/subscriptions?size=5&page=0", { signal }),
  });
  const icons = [CreditCard, Users, Package, Boxes];
  const firstName = user.name.split(" ")[0];
  const chartData = states.map((state, i) => ({
    name: human(state),
    count: breakdown[i].data?.totalElements || 0,
  }));
  return (
    <>
      <Heading
        eyebrow="Your workspace, at a glance"
        title={`Good to see you, ${firstName}`}
        description="Here’s what’s happening with your subscription relationships."
        action={
          <NewButton href="/subscriptions/new">New subscription</NewButton>
        }
      />
      <div
        className={`mb-7 grid gap-4 sm:grid-cols-2 ${endpoints.length > 2 ? "xl:grid-cols-4" : ""}`}
      >
        {endpoints.map((endpoint, i) => {
          const Icon = icons[i];
          const stat = stats[i];
          return (
            <Link
              key={endpoint}
              href={`/${endpoint}`}
              className="card group p-5 transition hover:border-violet-200"
            >
              <div className="mb-4 flex items-center justify-between">
                <span
                  className={`flex size-9 items-center justify-center rounded-xl ${i % 2 === 0 ? "bg-violet-50 text-violet-500" : "bg-teal-50 text-teal-600"}`}
                >
                  <Icon size={18} />
                </span>
                <ArrowUpRight className="size-4 text-slate-300 group-hover:text-violet-500" />
              </div>
              <div className="text-[28px] font-semibold tracking-tight">
                {stat.isPending ? (
                  <span className="inline-block h-8 w-14 animate-pulse rounded bg-slate-100" />
                ) : stat.error ? (
                  "—"
                ) : (
                  stat.data.totalElements.toLocaleString()
                )}
              </div>
              <p className="mt-1 text-xs text-slate-500">
                {user.role === "CUSTOMER"
                  ? "My subscriptions"
                  : `Total ${endpoint}`}
              </p>
              {stat.error && (
                <p className="mt-2 text-[10px] text-rose-500">
                  Couldn’t load this count
                </p>
              )}
            </Link>
          );
        })}
      </div>
      <div className="mb-7 grid gap-6 xl:grid-cols-[1.45fr_1fr]">
        <section className="card p-6">
          <div className="mb-6 flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold">Subscription lifecycle</h2>
              <p className="mt-1.5 text-xs text-slate-400">
                A clear picture of your current subscriptions
              </p>
            </div>
            <span className="rounded-md border border-slate-200 px-2 py-1 text-[10px] text-slate-500">
              All records
            </span>
          </div>
          {breakdown.some((v) => v.error) ? (
            <ErrorState error={breakdown.find((v) => v.error)?.error} />
          ) : breakdown.some((v) => v.isPending) ? (
            <div className="h-56 animate-pulse rounded-lg bg-slate-50" />
          ) : (
            <StateChart data={chartData} />
          )}
        </section>
        <section className="relative overflow-hidden rounded-[14px] bg-[#29223e] p-7 text-white">
          <div className="relative z-10">
            <span className="mb-5 inline-flex size-10 items-center justify-center rounded-xl bg-white/10 text-violet-200">
              <Workflow size={21} />
            </span>
            <p className="text-[10px] font-medium uppercase tracking-[.16em] text-violet-300">
              Built for the long run
            </p>
            <h2 className="mt-3 max-w-xs text-[25px] font-semibold leading-tight tracking-tight">
              Every subscription is
              <br />a relationship in motion.
            </h2>
            <p className="mb-7 mt-4 max-w-xs text-xs leading-6 text-slate-300">
              Start with a draft, align the details, and move forward with
              clarity.
            </p>
            <Link
              href="/subscriptions"
              className="inline-flex items-center gap-2 rounded-lg bg-white px-4 py-2.5 text-xs font-semibold text-slate-800"
            >
              Explore subscriptions
              <ArrowRight size={14} />
            </Link>
          </div>
          <div className="absolute -bottom-28 -right-28 size-80 rounded-full border-[45px] border-violet-400/10" />
        </section>
      </div>
      <section className="card overflow-hidden">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-5">
          <div>
            <h2 className="text-sm font-semibold">Recent subscriptions</h2>
            <p className="mt-1.5 text-xs text-slate-400">
              Your latest records and where they stand
            </p>
          </div>
          <Link
            href="/subscriptions"
            className="flex items-center gap-1 text-xs font-medium text-violet-600"
          >
            View all
            <ChevronRight size={14} />
          </Link>
        </div>
        {recent.isPending ? (
          <div className="space-y-4 p-6">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-10 animate-pulse rounded bg-slate-50" />
            ))}
          </div>
        ) : recent.error ? (
          <div className="p-5">
            <ErrorState error={recent.error} retry={() => recent.refetch()} />
          </div>
        ) : !recent.data.content.length ? (
          <Empty
            title="A fresh start"
            description="Create your first subscription to see your workspace come to life."
            action={
              <NewButton href="/subscriptions/new">
                Create subscription
              </NewButton>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>Subscription</th>
                  {user.role !== "CUSTOMER" && <th>Customer</th>}
                  <th>Plan</th>
                  <th>State</th>
                  <th>Amount</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {recent.data.content.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <Link href={`/subscriptions/${s.id}`}>{s.number}</Link>
                      <p className="mt-1 text-[10px] text-slate-400">
                        {s.startDate}
                      </p>
                    </td>
                    {user.role !== "CUSTOMER" && <td>{s.customerName}</td>}
                    <td className="text-slate-500">{s.planName}</td>
                    <td>
                      <Badge value={s.state} />
                    </td>
                    <td className="font-medium">{money(s.total)}</td>
                    <td>
                      <Link
                        aria-label={`View ${s.number}`}
                        href={`/subscriptions/${s.id}`}
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
      <p className="mt-4 text-[10px] text-slate-400">
        Live operational counts from your workspace. Subscription amounts are
        record totals.
      </p>
    </>
  );
}
