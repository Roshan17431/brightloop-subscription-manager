"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowRight, Check, Layers3, LoaderCircle } from "lucide-react";
import { api, errorMessage } from "@/lib/api";
import type { User } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/shared";
import {
  authSchema,
  registerSchema,
  loginSchema,
  resetSchema,
} from "./schemas";
export function AuthScreen({
  mode,
}: {
  mode: "login" | "register" | "forgot-password" | "reset-password";
}) {
  const router = useRouter();
  const params = useSearchParams();
  const client = useQueryClient();
  const [notice, setNotice] = useState("");
  const [serverError, setServerError] = useState("");
  const reset = mode === "reset-password";
  const forgot = mode === "forgot-password";
  const register = mode === "register";
  const schema = register
    ? registerSchema
    : forgot || reset
      ? authSchema
      : loginSchema;
  const form = useForm<z.infer<typeof authSchema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: "",
      email: reset ? "reset@example.invalid" : "",
      password: "",
    },
  });
  const resetForm = useForm<z.infer<typeof resetSchema>>({
    resolver: zodResolver(resetSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });
  const title = register
    ? "Create your account"
    : forgot
      ? "Forgot your password?"
      : reset
        ? "Set a new password"
        : "Welcome back";
  async function submit(values: z.infer<typeof authSchema>) {
    setServerError("");
    try {
      if (forgot) {
        await api("/auth/forgot-password", {
          method: "POST",
          body: { email: values.email },
        });
        setNotice(
          "If an account exists for that email, a password reset link is on its way. Check your inbox.",
        );
        return;
      }
      if (register)
        await api<User>("/auth/register", { method: "POST", body: values });
      const user = await api<User>("/auth/login", {
        method: "POST",
        body: { email: values.email, password: values.password },
      });
      client.clear();
      if (user?.role) client.setQueryData(["me"], user);
      toast.success(register ? "Your account is ready." : "Welcome back.");
      router.replace("/dashboard");
    } catch (error) {
      setServerError(errorMessage(error));
    }
  }
  async function submitReset(values: z.infer<typeof resetSchema>) {
    setServerError("");
    const token = params.get("token");
    if (!token) {
      setServerError(
        "This reset link is incomplete. Request a new password reset email.",
      );
      return;
    }
    try {
      await api("/auth/reset-password", {
        method: "POST",
        body: { token, password: values.password },
      });
      toast.success("Password updated. Sign in to continue.");
      router.replace("/login");
    } catch (error) {
      setServerError(errorMessage(error));
    }
  }
  return (
    <main className="grid min-h-screen lg:grid-cols-[.95fr_1.05fr]">
      <aside className="relative hidden overflow-hidden bg-[#25213e] p-12 text-white lg:flex lg:flex-col">
        <Link
          href="/"
          className="flex items-center gap-3 text-xl font-semibold"
        >
          <span className="flex size-10 items-center justify-center rounded-xl bg-violet-500">
            <Layers3 size={23} />
          </span>
          brightloop<span className="text-violet-400">.</span>
        </Link>
        <div className="relative z-10 my-auto max-w-md py-20">
          <span className="rounded-full border border-white/15 px-3 py-1.5 text-xs text-violet-200">
            A little clarity goes a long way.
          </span>
          <h2 className="mt-8 text-5xl font-semibold leading-[1.15] tracking-tight">
            Good business.
            <br />
            Great relationships.
            <br />
            <span className="text-violet-300">One clear view.</span>
          </h2>
          <p className="mt-6 max-w-sm text-sm leading-7 text-slate-300">
            Bring your customers, products, and subscriptions together in a
            workspace that keeps things moving.
          </p>
          <div className="mt-10 flex items-center gap-2 text-xs text-violet-200">
            <Check size={15} />
            Built for the way your business grows
          </div>
        </div>
        <div className="absolute -bottom-36 -right-32 size-[500px] rounded-full border-[65px] border-violet-400/10" />
        <p className="text-xs text-slate-400">
          A brighter way to stay in the loop.
        </p>
      </aside>
      <section className="flex items-center justify-center bg-[#fcfcfe] px-6 py-12">
        <div className="w-full max-w-[390px]">
          <Link
            href="/"
            className="mb-12 flex items-center gap-2 text-xl font-semibold lg:hidden"
          >
            <Layers3 className="text-violet-600" />
            brightloop.
          </Link>
          <p className="mb-3 text-xs font-semibold uppercase tracking-[.18em] text-violet-600">
            Your business, connected
          </p>
          <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
          <p className="mb-8 mt-3 text-sm leading-6 text-slate-500">
            {register
              ? "Get started with your own customer workspace."
              : forgot
                ? "We’ll email you a secure link to get you back in."
                : reset
                  ? "Choose a strong password to secure your account."
                  : "Sign in to pick up where you left off."}
          </p>
          {notice ? (
            <div
              role="status"
              className="rounded-xl border border-teal-100 bg-teal-50 p-5 text-sm leading-6 text-teal-800"
            >
              {notice}
            </div>
          ) : reset ? (
            <form
              onSubmit={resetForm.handleSubmit(submitReset)}
              className="space-y-5"
              noValidate
            >
              <FormField
                label="New password"
                error={resetForm.formState.errors.password?.message}
                hint="9+ characters, uppercase, lowercase and a special character."
              >
                <input
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  {...resetForm.register("password")}
                />
              </FormField>
              <FormField
                label="Confirm password"
                error={resetForm.formState.errors.confirmPassword?.message}
              >
                <input
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  {...resetForm.register("confirmPassword")}
                />
              </FormField>
              {serverError && (
                <p role="alert" className="text-sm text-rose-600">
                  {serverError}
                </p>
              )}
              <Button
                className="w-full"
                disabled={resetForm.formState.isSubmitting}
              >
                {resetForm.formState.isSubmitting ? (
                  <LoaderCircle className="animate-spin" />
                ) : (
                  <>
                    Update password
                    <ArrowRight />
                  </>
                )}
              </Button>
            </form>
          ) : (
            <form
              onSubmit={form.handleSubmit(submit)}
              className="space-y-5"
              noValidate
            >
              {register && (
                <FormField
                  label="Full name"
                  error={form.formState.errors.name?.message}
                >
                  <input
                    className="input"
                    autoComplete="name"
                    placeholder="Your full name"
                    {...form.register("name")}
                  />
                </FormField>
              )}
              <FormField
                label="Email address"
                error={form.formState.errors.email?.message}
              >
                <input
                  className="input"
                  type="email"
                  autoComplete="email"
                  placeholder="you@company.com"
                  {...form.register("email")}
                />
              </FormField>
              {!forgot && (
                <FormField
                  label="Password"
                  error={form.formState.errors.password?.message}
                  hint={
                    register
                      ? "9+ characters, uppercase, lowercase and a special character."
                      : undefined
                  }
                >
                  <input
                    className="input"
                    type="password"
                    autoComplete={
                      register ? "new-password" : "current-password"
                    }
                    placeholder="Enter your password"
                    {...form.register("password")}
                  />
                </FormField>
              )}
              {!register && !forgot && (
                <div className="text-right">
                  <Link
                    href="/forgot-password"
                    className="text-xs font-medium text-violet-600 hover:underline"
                  >
                    Forgot password?
                  </Link>
                </div>
              )}
              {serverError && (
                <p role="alert" className="text-sm text-rose-600">
                  {serverError}
                </p>
              )}
              <Button className="w-full" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? (
                  <LoaderCircle className="animate-spin" />
                ) : (
                  <>
                    {forgot
                      ? "Send reset link"
                      : register
                        ? "Create account"
                        : "Sign in"}
                    <ArrowRight />
                  </>
                )}
              </Button>
            </form>
          )}
          <p className="mt-7 text-center text-xs text-slate-500">
            {register
              ? "Already have an account?"
              : forgot || reset
                ? "Ready to sign in?"
                : "New to Brightloop?"}{" "}
            <Link
              className="font-semibold text-violet-600"
              href={register || forgot || reset ? "/login" : "/register"}
            >
              {register || forgot || reset ? "Sign in" : "Create an account"}
            </Link>
          </p>
          <div className="mt-12 border-t border-slate-100 pt-6 text-center text-[11px] text-slate-400">
            Brightloop · Subscription management, made clear.
          </div>
        </div>
      </section>
    </main>
  );
}
