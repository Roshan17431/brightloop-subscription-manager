import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, queryString } from "./api";
afterEach(() => vi.unstubAllGlobals());
describe("typed API boundary", () => {
  it("obtains CSRF in the current session and sends cookies and header on writes", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            data: { token: "test-csrf", headerName: "X-CSRF-TOKEN" },
          }),
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ data: { id: 42 } })),
      );
    vi.stubGlobal("fetch", fetch);
    expect(
      await api("/products", { method: "POST", body: { name: "Service" } }),
    ).toEqual({ id: 42 });
    expect(fetch.mock.calls[0][0]).toBe("/api/v1/auth/csrf");
    expect(fetch.mock.calls[1][1]).toMatchObject({
      method: "POST",
      credentials: "include",
      headers: {
        "X-CSRF-TOKEN": "test-csrf",
        "Content-Type": "application/json",
      },
      body: '{"name":"Service"}',
    });
  });
  it("preserves standardized errors and unauthorized status", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            message: "Authentication required",
            errorCode: "UNAUTHENTICATED",
            details: { field: "email" },
          }),
          { status: 401 },
        ),
      ),
    );
    await expect(api("/auth/me")).rejects.toMatchObject({
      status: 401,
      code: "UNAUTHENTICATED",
      message: "Authentication required",
      details: { field: "email" },
    });
  });
  it("supports legacy nested error envelopes", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            error: { message: "Forbidden", code: "FORBIDDEN" },
          }),
          { status: 403 },
        ),
      ),
    );
    await expect(api("/customers")).rejects.toBeInstanceOf(ApiError);
  });
  it("handles a successful no-content deletion", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              data: { token: "csrf", headerName: "X-CSRF-TOKEN" },
            }),
          ),
        )
        .mockResolvedValueOnce(new Response(null, { status: 204 })),
    );
    expect(await api("/products/1", { method: "DELETE" })).toBeUndefined();
  });
  it("encodes query values and omits empty filters", () =>
    expect(queryString({ q: "One & Two", page: 0, status: "" })).toBe(
      "?q=One+%26+Two&page=0",
    ));
});
