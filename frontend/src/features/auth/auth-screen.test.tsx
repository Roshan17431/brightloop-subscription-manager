import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthScreen } from "./auth-screen";
import { Shell } from "@/components/shell";
const { request, replace, push } = vi.hoisted(() => ({
  request: vi.fn(),
  replace: vi.fn(),
  push: vi.fn(),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  api: request,
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/dashboard",
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
import { ApiError } from "@/lib/api";
function wrapped(node: React.ReactNode) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      {node}
    </QueryClientProvider>,
  );
}
afterEach(cleanup);
beforeEach(() => {
  request.mockReset();
  replace.mockReset();
  push.mockReset();
});
describe("authentication UI", () => {
  it("shows field validation before making a login request", async () => {
    wrapped(<AuthScreen mode="login" />);
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(
      await screen.findByText("Enter a valid email address."),
    ).toBeVisible();
    expect(request).not.toHaveBeenCalled();
  });
  it("shows API login errors without navigating", async () => {
    request.mockRejectedValue(new ApiError(401, "Invalid email or password"));
    wrapped(<AuthScreen mode="login" />);
    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "rin@example.test" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "StrongPass!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Invalid email or password")).toBeVisible();
    expect(replace).not.toHaveBeenCalled();
  });
  it("establishes login after registration before entering the workspace", async () => {
    const user = {
      id: 1,
      name: "Rin",
      email: "rin@example.test",
      role: "CUSTOMER",
    };
    request.mockResolvedValue(user);
    wrapped(<AuthScreen mode="register" />);
    fireEvent.change(screen.getByLabelText("Full name"), {
      target: { value: "Rin" },
    });
    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: user.email },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "StrongPass!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/dashboard"));
    expect(request.mock.calls.map((call) => call[0])).toEqual([
      "/auth/register",
      "/auth/login",
    ]);
  });
  it("redirects an expired session to login without rendering private children", async () => {
    request.mockRejectedValue(new ApiError(401, "Authentication required"));
    wrapped(<Shell>{() => <span>Private workspace</span>}</Shell>);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
    expect(screen.queryByText("Private workspace")).not.toBeInTheDocument();
  });
});
