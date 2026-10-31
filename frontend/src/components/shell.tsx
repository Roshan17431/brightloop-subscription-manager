"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  ArrowUpRight,
  Boxes,
  ChevronDown,
  CircleHelp,
  CreditCard,
  LayoutDashboard,
  Layers3,
  LogOut,
  Menu,
  Package,
  PanelLeftClose,
  Settings2,
  Users,
  X,
} from "lucide-react";
import { api, ApiError, errorMessage } from "@/lib/api";
import type { User } from "@/lib/types";
import { Button } from "./ui/button";
import { ErrorState, Loading } from "./shared";
export function Shell({
  children,
}: {
  children: (user: User) => React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const client = useQueryClient();
  const [mobile, setMobile] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const me = useQuery({
    queryKey: ["me"],
    queryFn: ({ signal }) => api<User>("/auth/me", { signal }),
  });
  useEffect(() => {
    if (me.error instanceof ApiError && me.error.status === 401)
      router.replace("/login");
  }, [me.error, router]);
  useEffect(() => {
    setMobile(false);
  }, [pathname]);
  async function logout() {
    setLoggingOut(true);
    try {
      await api("/auth/logout", { method: "POST" });
      client.clear();
      router.replace("/login");
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setLoggingOut(false);
    }
  }
  if (me.isPending || (me.error instanceof ApiError && me.error.status === 401))
    return (
      <div className="mx-auto max-w-5xl p-12">
        <Loading />
      </div>
    );
  if (me.error)
    return (
      <div className="mx-auto max-w-xl p-12">
        <ErrorState error={me.error} retry={() => me.refetch()} />
      </div>
    );
  const user = me.data;
  const nav = [
    { label: "Overview", href: "/dashboard", icon: LayoutDashboard },
    { label: "Subscriptions", href: "/subscriptions", icon: CreditCard },
    ...(user.role !== "CUSTOMER"
      ? [
          { label: "Customers", href: "/customers", icon: Users },
          { label: "Products", href: "/products", icon: Package },
          { label: "Plans", href: "/plans", icon: Boxes },
        ]
      : []),
    ...(user.role === "ADMIN"
      ? [{ label: "Team members", href: "/team", icon: Users }]
      : []),
  ];
  return (
    <div className="min-h-screen">
      {mobile && (
        <button
          aria-label="Close navigation"
          onClick={() => setMobile(false)}
          className="fixed inset-0 z-30 bg-slate-950/25 lg:hidden"
        />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-[232px] flex-col border-r border-slate-200/80 bg-white px-4 py-7 transition-transform lg:translate-x-0 ${mobile ? "translate-x-0" : "-translate-x-full"}`}
      >
        <Link
          href="/dashboard"
          className="mb-9 flex items-center gap-2.5 px-3 text-[22px] font-bold tracking-tight"
        >
          <span className="flex size-8 items-center justify-center rounded-[10px] bg-violet-600 text-white">
            <Layers3 size={20} />
          </span>
          brightloop<span className="-ml-2 text-violet-500">.</span>
        </Link>
        <div className="mx-1 mb-7 flex items-center gap-3 rounded-xl border border-slate-200 p-3">
          <div className="flex size-9 items-center justify-center rounded-lg bg-violet-50 text-sm font-semibold text-violet-600">
            B
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-xs font-semibold">
              {user.role === "CUSTOMER"
                ? "My workspace"
                : "Brightloop workspace"}
            </p>
            <p className="mt-1 text-[10px] text-slate-400">
              {user.role === "CUSTOMER"
                ? "Customer account"
                : "Business workspace"}
            </p>
          </div>
          <ChevronDown size={13} className="text-slate-400" />
        </div>
        <p className="mb-3 px-3 text-[9px] font-semibold uppercase tracking-[.18em] text-slate-400">
          Workspace
        </p>
        <nav className="space-y-1">
          {nav.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 rounded-lg px-3 py-3 text-xs font-medium ${pathname === item.href || pathname.startsWith(item.href + "/") ? "bg-violet-50 text-violet-700" : "text-slate-500 hover:bg-slate-50 hover:text-slate-800"}`}
            >
              <item.icon size={17} />
              {item.label}
              {pathname === item.href && (
                <span className="ml-auto size-1.5 rounded-full bg-violet-500" />
              )}
            </Link>
          ))}
        </nav>
        <div className="mt-auto pt-8">
          <div className="mx-1 mb-5 rounded-xl bg-[#f7f6fc] p-4">
            <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-slate-700">
              <span className="text-violet-500">✧</span> Everything in its place
            </div>
            <p className="text-[11px] leading-5 text-slate-400">
              Keep your subscriptions moving and your customers connected.
            </p>
          </div>
          <Link
            href="/profile"
            className="flex items-center gap-3 rounded-lg px-3 py-3 text-xs text-slate-500 hover:bg-slate-50"
          >
            <Settings2 size={17} />
            My profile
          </Link>
          <button
            disabled={loggingOut}
            onClick={logout}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-3 text-xs text-slate-500 hover:bg-slate-50"
          >
            <LogOut size={17} />
            {loggingOut ? "Signing out…" : "Sign out"}
          </button>
        </div>
        <button
          className="absolute right-3 top-3 lg:hidden"
          aria-label="Close menu"
          onClick={() => setMobile(false)}
        >
          <X size={16} />
        </button>
      </aside>
      <div className="lg:pl-[232px]">
        <header className="flex h-[76px] items-center justify-between border-b border-slate-200/70 bg-white/85 px-5 sm:px-9">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden"
              aria-label="Open navigation"
              onClick={() => setMobile(true)}
            >
              <Menu />
            </Button>
            <span className="hidden text-xs text-slate-400 sm:inline">
              Workspace
            </span>
            <span className="hidden text-slate-300 sm:inline">/</span>
            <span className="text-xs font-medium text-slate-600">
              {nav.find((item) => pathname.startsWith(item.href))?.label ||
                "My profile"}
            </span>
          </div>
          <Link href="/profile" className="flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-xs font-semibold">{user.name}</p>
              <p className="mt-1 text-[10px] capitalize text-slate-400">
                {user.role.toLowerCase()}
              </p>
            </div>
            <div className="flex size-9 items-center justify-center rounded-full border-2 border-white bg-[#e9e4f8] text-xs font-semibold text-violet-700 shadow-sm">
              {user.name
                .split(" ")
                .map((p) => p[0])
                .slice(0, 2)
                .join("")}
            </div>
          </Link>
        </header>
        <main className="mx-auto w-full min-w-0 max-w-[1500px] px-5 py-8 sm:px-9 sm:py-9">
          {children(user)}
        </main>
        <footer className="mx-5 flex justify-between border-t border-slate-200/70 py-6 text-[10px] text-slate-400 sm:mx-9">
          <span>© {new Date().getFullYear()} Brightloop</span>
          <span>Keep good things going.</span>
        </footer>
      </div>
    </div>
  );
}
