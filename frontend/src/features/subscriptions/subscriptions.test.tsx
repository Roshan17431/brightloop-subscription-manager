import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AssociatedProductPicker } from "./subscriptions";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: request,
}));
afterEach(() => {
  cleanup();
  request.mockReset();
});
function renderPicker(ids?: number[], onChange = vi.fn()) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <AssociatedProductPicker productIds={ids} value={0} onChange={onChange} />
    </QueryClientProvider>,
  );
}
describe("subscription product associations", () => {
  it("waits for a selected plan before offering products", () => {
    renderPicker();
    expect(screen.getByRole("combobox", { name: "Product" })).toBeDisabled();
    expect(request).not.toHaveBeenCalled();
  });
  it("offers only the selected plan’s active associated products", async () => {
    request.mockImplementation((path: string) =>
      Promise.resolve({
        id: Number(path.split("/").pop()),
        name: path.endsWith("1") ? "Included service" : "Inactive service",
        status: path.endsWith("1") ? "ACTIVE" : "INACTIVE",
        salesPrice: 100,
      }),
    );
    const change = vi.fn();
    renderPicker([1, 2], change);
    expect(
      await screen.findByRole("option", { name: "Included service · 100.00" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: /Inactive service/ }),
    ).not.toBeInTheDocument();
    expect(request.mock.calls.map((call) => call[0])).toEqual([
      "/products/1",
      "/products/2",
    ]);
    fireEvent.change(screen.getByRole("combobox", { name: "Product" }), {
      target: { value: "1" },
    });
    expect(change).toHaveBeenCalledWith(1);
  });
});
