import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Shell } from "./shell";
import { ApiError } from "@/lib/api";

const replaceMock = vi.fn();
const pushMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  usePathname: () => "/dashboard",
  useSearchParams: () => new URLSearchParams(),
}));

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: request,
}));

afterEach(() => {
  cleanup();
  request.mockReset();
  replaceMock.mockReset();
  pushMock.mockReset();
});

describe("Phase 5 Workspace Shell, RBAC Navigation & Accessibility", () => {
  it("isolates CUSTOMER navigation from internal admin/staff modules", async () => {
    request.mockResolvedValue({
      id: 10,
      name: "Meera Menon",
      email: "meera@customer.test",
      role: "CUSTOMER",
      customerId: 4,
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <Shell>{(user) => <div>Welcome {user.name}</div>}</Shell>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Welcome Meera Menon")).toBeInTheDocument();
    expect(screen.getByText("My workspace")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Subscriptions" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Quotations" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Invoices" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Payments" })).toBeInTheDocument();

    // Restricted internal links must not render for CUSTOMER
    expect(screen.queryByRole("link", { name: "Reports" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Billing" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Customers" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Taxes" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Discounts" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Team members" })).not.toBeInTheDocument();
  });

  it("shows operational modules to STAFF while hiding ADMIN-only Team members", async () => {
    request.mockResolvedValue({
      id: 2,
      name: "Rahul Nair",
      email: "rahul@brightloop.local",
      role: "STAFF",
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <Shell>{(user) => <div>Staff workspace {user.role}</div>}</Shell>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Staff workspace STAFF")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Reports" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Billing" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Customers" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Taxes" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Discounts" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Team members" })).not.toBeInTheDocument();
  });

  it("supports mobile navigation drawer toggle and sign-out session clearing", async () => {
    request.mockImplementation((path: string) => {
      if (path === "/auth/me") {
        return Promise.resolve({
          id: 1,
          name: "Admin User",
          email: "admin@brightloop.local",
          role: "ADMIN",
        });
      }
      return Promise.resolve({ message: "Logged out" });
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <Shell>{() => <div>Admin Content</div>}</Shell>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Admin Content")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Team members" })).toBeInTheDocument();

    // Open mobile navigation drawer
    fireEvent.click(screen.getByRole("button", { name: "Open navigation" }));
    const closeBackdrop = screen.getByRole("button", {
      name: "Close navigation",
    });
    expect(closeBackdrop).toBeInTheDocument();
    fireEvent.click(closeBackdrop);

    // Sign out
    fireEvent.click(screen.getByRole("button", { name: /Sign out/i }));
    await vi.waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/login");
    });
  });

  it("redirects unauthenticated 401 session responses to /login", async () => {
    request.mockRejectedValue(
      new ApiError(401, "UNAUTHENTICATED", "Authentication required"),
    );

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <Shell>{() => <div>Protected</div>}</Shell>
      </QueryClientProvider>,
    );

    await vi.waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/login");
    });
  });
});
