"use client";
import { Suspense, useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Shell } from "@/components/shell";
import { Empty, Loading } from "@/components/shared";
import { Button } from "@/components/ui/button";
import { AuthScreen } from "@/features/auth/auth-screen";
import { Dashboard } from "@/features/dashboard/dashboard";
import {
  CatalogList,
  CatalogDetail,
  ProductEditor,
  PlanEditor,
} from "@/features/catalog/catalog";
import {
  CustomerList,
  CustomerDetail,
  CustomerEditor,
  StaffProfile,
  Team,
} from "@/features/customers/customers";
import {
  SubscriptionList,
  SubscriptionDetail,
  SubscriptionEditor,
} from "@/features/subscriptions/subscriptions";
import { InvoiceList, InvoiceDetail } from "@/features/billing/invoices";
import {
  PaymentList,
  PaymentDetail,
  RecordPaymentEditor,
} from "@/features/payments/payments";
import {
  QuotationList,
  QuotationDetail,
  QuotationTemplateDetail,
  QuotationTemplateEditor,
} from "@/features/quotations/quotations";
import {
  BillingDashboard,
  BillingRunDetail,
} from "@/features/billing/billing";
import {
  TaxList,
  TaxDetail,
  TaxEditor,
  DiscountList,
  DiscountDetail,
  DiscountEditor,
} from "@/features/billing/taxes-discounts";
import type { User } from "@/lib/types";
function App() {
  const pathname = usePathname();
  const router = useRouter();
  useEffect(() => {
    if (pathname === "/") router.replace("/dashboard");
  }, [pathname, router]);
  if (pathname === "/") return <Loading />;
  const auth = pathname.slice(1);
  if (
    auth === "login" ||
    auth === "register" ||
    auth === "forgot-password" ||
    auth === "reset-password"
  )
    return <AuthScreen mode={auth} />;
  return (
    <Shell>
      {(user) => (
        <Workspace path={pathname.split("/").filter(Boolean)} user={user} />
      )}
    </Shell>
  );
}
function Workspace({ path, user }: { path: string[]; user: User }) {
  const [section, id, action] = path;
  const forbidden = () => (
    <Empty
      title="This page is restricted"
      description="Your account doesn’t have permission to use this part of the workspace."
      action={
        <Button asChild>
          <Link href="/dashboard">Return to overview</Link>
        </Button>
      }
    />
  );
  if (section === "dashboard") return <Dashboard user={user} />;
  if (section === "profile")
    return user.role === "CUSTOMER" ? (
      <CustomerEditor profile user={user} />
    ) : (
      <StaffProfile user={user} />
    );
  if (section === "team") return user.role === "ADMIN" ? <Team /> : forbidden();
  if (section === "customers") {
    if (user.role === "CUSTOMER") return forbidden();
    if (!id) return <CustomerList />;
    if (id === "new" || action === "edit")
      return <CustomerEditor id={id === "new" ? undefined : id} />;
    return <CustomerDetail id={id} />;
  }
  if (section === "products" || section === "plans") {
    if (user.role === "CUSTOMER") return forbidden();
    if (!id) return <CatalogList kind={section} user={user} />;
    if (id === "new" || action === "edit") {
      if (user.role !== "ADMIN") return forbidden();
      return section === "products" ? (
        <ProductEditor id={id === "new" ? undefined : id} />
      ) : (
        <PlanEditor id={id === "new" ? undefined : id} />
      );
    }
    return <CatalogDetail kind={section} id={id} user={user} />;
  }
  if (section === "subscriptions") {
    if (!id) return <SubscriptionList user={user} />;
    if (id === "new" || action === "edit")
      return (
        <SubscriptionEditor id={id === "new" ? undefined : id} user={user} />
      );
    return <SubscriptionDetail id={id} user={user} />;
  }
  if (section === "invoices") {
    if (!id) return <InvoiceList user={user} />;
    return <InvoiceDetail id={id} user={user} />;
  }
  if (section === "payments") {
    if (!id) return <PaymentList user={user} />;
    if (id === "new") {
      if (user.role === "CUSTOMER") return forbidden();
      return <RecordPaymentEditor />;
    }
    return <PaymentDetail id={id} user={user} />;
  }
  if (section === "quotations") {
    if (!id) return <QuotationList user={user} />;
    return <QuotationDetail id={id} user={user} />;
  }
  if (section === "quotation-templates") {
    if (user.role === "CUSTOMER") return forbidden();
    if (!id) return <QuotationList user={user} initialTab="templates" />;
    if (id === "new" || action === "edit") {
      return <QuotationTemplateEditor id={id === "new" ? undefined : id} />;
    }
    return <QuotationTemplateDetail id={id} user={user} />;
  }
  if (section === "billing") {
    if (user.role === "CUSTOMER") return forbidden();
    if (!id) return <BillingDashboard user={user} />;
    return <BillingRunDetail id={id} user={user} />;
  }
  if (section === "taxes") {
    if (user.role === "CUSTOMER") return forbidden();
    if (!id) return <TaxList user={user} />;
    if (id === "new" || action === "edit") {
      if (user.role !== "ADMIN") return forbidden();
      return <TaxEditor id={id === "new" ? undefined : id} />;
    }
    return <TaxDetail id={id} user={user} />;
  }
  if (section === "discounts") {
    if (user.role === "CUSTOMER") return forbidden();
    if (!id) return <DiscountList user={user} />;
    if (id === "new" || action === "edit") {
      if (user.role !== "ADMIN") return forbidden();
      return <DiscountEditor id={id === "new" ? undefined : id} />;
    }
    return <DiscountDetail id={id} user={user} />;
  }
  return (
    <Empty
      title="Page not found"
      description="This page doesn’t exist in your workspace."
      action={
        <Button asChild>
          <Link href="/dashboard">Return to overview</Link>
        </Button>
      }
    />
  );
}
export default function Page() {
  return (
    <Suspense
      fallback={
        <div className="p-10">
          <Loading />
        </div>
      }
    >
      <App />
    </Suspense>
  );
}
