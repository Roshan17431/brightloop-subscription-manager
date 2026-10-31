"use client";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type {
  AgingBucketItem,
  PaymentMethodBreakdownItem,
  PlanBreakdownItem,
  RevenueTrendPoint,
  StatusCountItem,
} from "@/lib/types";
import { human, money } from "@/lib/utils";

const STATUS_COLORS = [
  "#cbd5e1",
  "#c4b5fd",
  "#818cf8",
  "#7c3aed",
  "#f59e0b",
  "#64748b",
];

const AGING_COLORS = ["#f59e0b", "#f97316", "#ef4444", "#991b1b"];
const METHOD_COLORS = ["#7c3aed", "#0d9488", "#2563eb", "#db2777", "#64748b"];

function num(v: number | string | undefined): number {
  if (v === undefined || v === null) return 0;
  const parsed = typeof v === "number" ? v : Number.parseFloat(String(v));
  return Number.isFinite(parsed) ? parsed : 0;
}

export function RevenueTrendChart({ data }: { data: RevenueTrendPoint[] }) {
  const chartData = data.map((d) => ({
    label: d.label,
    invoicedRevenue: num(d.invoicedRevenue),
    subtotal: num(d.subtotal),
    collectedPayments: num(d.collectedPayments),
  }));

  return (
    <div
      className="h-64 w-full"
      role="img"
      aria-label={`Revenue trend chart with ${chartData.length} periods`}
    >
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart
          data={chartData}
          margin={{ top: 10, right: 12, left: 0, bottom: 0 }}
        >
          <defs>
            <linearGradient id="revGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#7c3aed" stopOpacity={0.24} />
              <stop offset="95%" stopColor="#7c3aed" stopOpacity={0.02} />
            </linearGradient>
            <linearGradient id="colGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#0d9488" stopOpacity={0.22} />
              <stop offset="95%" stopColor="#0d9488" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid
            strokeDasharray="3 5"
            vertical={false}
            stroke="#edf0f5"
          />
          <XAxis
            dataKey="label"
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            dy={6}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            tickFormatter={(v) => `₹${Number(v).toLocaleString("en-IN")}`}
          />
          <Tooltip
            formatter={(value: unknown) => money(Number(value ?? 0))}
            contentStyle={{
              borderRadius: 10,
              borderColor: "#ede9fe",
              fontSize: 12,
            }}
          />
          <Legend wrapperStyle={{ fontSize: 11 }} />
          <Area
            type="monotone"
            dataKey="invoicedRevenue"
            name="Invoiced Revenue"
            stroke="#7c3aed"
            strokeWidth={2}
            fill="url(#revGrad)"
          />
          <Area
            type="monotone"
            dataKey="collectedPayments"
            name="Collected Payments"
            stroke="#0d9488"
            strokeWidth={2}
            fill="url(#colGrad)"
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function InvoicedVsCollectedChart({
  data,
}: {
  data: RevenueTrendPoint[];
}) {
  const chartData = data.map((d) => ({
    label: d.label,
    invoiced: num(d.invoicedRevenue),
    collected: num(d.collectedPayments),
    outstanding: num(d.outstandingAmount),
  }));

  return (
    <div
      className="h-64 w-full"
      role="img"
      aria-label={`Invoiced vs collected comparison chart with ${chartData.length} periods`}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={chartData}
          barGap={4}
          margin={{ top: 10, right: 12, left: 0, bottom: 0 }}
        >
          <CartesianGrid
            strokeDasharray="3 5"
            vertical={false}
            stroke="#edf0f5"
          />
          <XAxis
            dataKey="label"
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            dy={6}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            tickFormatter={(v) => `₹${Number(v).toLocaleString("en-IN")}`}
          />
          <Tooltip
            formatter={(value: unknown) => money(Number(value ?? 0))}
            contentStyle={{
              borderRadius: 10,
              borderColor: "#ede9fe",
              fontSize: 12,
            }}
          />
          <Legend wrapperStyle={{ fontSize: 11 }} />
          <Bar
            dataKey="invoiced"
            name="Invoiced"
            fill="#7c3aed"
            radius={[4, 4, 0, 0]}
          />
          <Bar
            dataKey="collected"
            name="Collected"
            fill="#0d9488"
            radius={[4, 4, 0, 0]}
          />
          <Bar
            dataKey="outstanding"
            name="Outstanding"
            fill="#f59e0b"
            radius={[4, 4, 0, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function PaymentMethodsChart({
  data,
}: {
  data: PaymentMethodBreakdownItem[];
}) {
  const chartData = data.map((d) => ({
    name: human(d.method),
    amount: num(d.amount),
    count: d.count,
  }));

  return (
    <div
      className="h-64 w-full"
      role="img"
      aria-label={chartData.map((d) => `${d.name}: ${d.amount}`).join(", ")}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={chartData}
          barSize={36}
          margin={{ top: 10, right: 12, left: 0, bottom: 0 }}
        >
          <CartesianGrid
            strokeDasharray="3 5"
            vertical={false}
            stroke="#edf0f5"
          />
          <XAxis
            dataKey="name"
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            dy={6}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            tickFormatter={(v) => `₹${Number(v).toLocaleString("en-IN")}`}
          />
          <Tooltip
            formatter={(value: unknown) => money(Number(value ?? 0))}
            contentStyle={{
              borderRadius: 10,
              borderColor: "#ede9fe",
              fontSize: 12,
            }}
          />
          <Bar dataKey="amount" name="Collected Amount" radius={[5, 5, 0, 0]}>
            {chartData.map((entry, idx) => (
              <Cell
                key={entry.name}
                fill={METHOD_COLORS[idx % METHOD_COLORS.length]}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function SubscriptionStatusChart({
  data,
}: {
  data: StatusCountItem[];
}) {
  const chartData = data.map((d) => ({
    name: human(d.state),
    count: d.count,
    mrr: num(d.mrr),
  }));

  return (
    <div
      className="h-64 w-full"
      role="img"
      aria-label={chartData.map((d) => `${d.name}: ${d.count}`).join(", ")}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={chartData}
          barSize={36}
          margin={{ top: 10, right: 12, left: -20, bottom: 0 }}
        >
          <CartesianGrid
            strokeDasharray="3 5"
            vertical={false}
            stroke="#edf0f5"
          />
          <XAxis
            dataKey="name"
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            dy={6}
          />
          <YAxis
            allowDecimals={false}
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
          />
          <Tooltip
            contentStyle={{
              borderRadius: 10,
              borderColor: "#ede9fe",
              fontSize: 12,
            }}
          />
          <Bar dataKey="count" name="Subscriptions" radius={[5, 5, 0, 0]}>
            {chartData.map((entry, idx) => (
              <Cell
                key={entry.name}
                fill={STATUS_COLORS[idx % STATUS_COLORS.length]}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function MrrByPlanChart({ data }: { data: PlanBreakdownItem[] }) {
  const chartData = data.slice(0, 8).map((d) => ({
    name: d.planName,
    mrr: num(d.mrr),
    arr: num(d.arr),
    activeCount: d.activeCount,
  }));

  return (
    <div
      className="h-64 w-full"
      role="img"
      aria-label={chartData.map((d) => `${d.name}: ${d.mrr}`).join(", ")}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={chartData}
          barSize={34}
          margin={{ top: 10, right: 12, left: 0, bottom: 0 }}
        >
          <CartesianGrid
            strokeDasharray="3 5"
            vertical={false}
            stroke="#edf0f5"
          />
          <XAxis
            dataKey="name"
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            dy={6}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            tickFormatter={(v) => `₹${Number(v).toLocaleString("en-IN")}`}
          />
          <Tooltip
            formatter={(value: unknown) => money(Number(value ?? 0))}
            contentStyle={{
              borderRadius: 10,
              borderColor: "#ede9fe",
              fontSize: 12,
            }}
          />
          <Bar
            dataKey="mrr"
            name="Active MRR"
            fill="#7c3aed"
            radius={[5, 5, 0, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function OverdueAgingChart({ data }: { data: AgingBucketItem[] }) {
  const chartData = data.map((d) => ({
    name: d.label,
    amount: num(d.amount),
    count: d.invoiceCount,
  }));

  return (
    <div
      className="h-64 w-full"
      role="img"
      aria-label={chartData.map((d) => `${d.name}: ${d.amount}`).join(", ")}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={chartData}
          barSize={38}
          margin={{ top: 10, right: 12, left: 0, bottom: 0 }}
        >
          <CartesianGrid
            strokeDasharray="3 5"
            vertical={false}
            stroke="#edf0f5"
          />
          <XAxis
            dataKey="name"
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            dy={6}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#64748b" }}
            tickFormatter={(v) => `₹${Number(v).toLocaleString("en-IN")}`}
          />
          <Tooltip
            formatter={(value: unknown) => money(Number(value ?? 0))}
            contentStyle={{
              borderRadius: 10,
              borderColor: "#fee2e2",
              fontSize: 12,
            }}
          />
          <Bar dataKey="amount" name="Overdue Balance" radius={[5, 5, 0, 0]}>
            {chartData.map((entry, idx) => (
              <Cell
                key={entry.name}
                fill={AGING_COLORS[idx % AGING_COLORS.length]}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
