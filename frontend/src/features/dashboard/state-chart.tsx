"use client";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
const colors = ["#ddd6fe", "#c4b5fd", "#a78bfa", "#7c3aed", "#5b21b6"];
export default function StateChart({
  data,
}: {
  data: { name: string; count: number }[];
}) {
  return (
    <div
      className="h-56 w-full"
      role="img"
      aria-label={data.map((v) => `${v.name}: ${v.count}`).join(", ")}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data}
          barSize={38}
          margin={{ top: 8, right: 5, left: -27, bottom: 0 }}
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
            tick={{ fontSize: 10, fill: "#94a3b8" }}
            dy={8}
          />
          <YAxis
            allowDecimals={false}
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 10, fill: "#94a3b8" }}
          />
          <Tooltip
            cursor={{ fill: "#f8f7ff" }}
            contentStyle={{
              borderRadius: 10,
              borderColor: "#ede9fe",
              fontSize: 12,
            }}
          />
          <Bar dataKey="count" name="Subscriptions" radius={[5, 5, 0, 0]}>
            {data.map((d, i) => (
              <Cell key={d.name} fill={colors[i]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
