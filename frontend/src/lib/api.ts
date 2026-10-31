const base = process.env.NEXT_PUBLIC_API_URL || "/api/v1";
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string,
    public details?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
async function read<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T;
  const body = await response.json().catch(() => null);
  if (!response.ok)
    throw new ApiError(
      response.status,
      body?.error?.message ||
        body?.message ||
        `Request failed (${response.status}).`,
      body?.error?.code || body?.errorCode,
      body?.error?.details || body?.details,
    );
  return body?.data as T;
}
export async function api<T>(
  path: string,
  options: { method?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<T> {
  const method = options.method || "GET";
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") {
    const csrf = await read<{ token: string; headerName: string }>(
      await fetch(`${base}/auth/csrf`, {
        credentials: "include",
        cache: "no-store",
        signal: options.signal,
      }),
    );
    headers[csrf.headerName] = csrf.token;
  }
  return read<T>(
    await fetch(`${base}${path}`, {
      method,
      headers,
      credentials: "include",
      cache: "no-store",
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    }),
  );
}
export function queryString(
  values: Record<string, string | number | boolean | undefined>,
) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return `?${params}`;
}
export function errorMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
}
