import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
export const cn = (...values: ClassValue[]) => twMerge(clsx(values));

type DecimalAmount = string | number;

/** Keep API decimal strings exact; arithmetic uses integer cents throughout. */
function minorUnits(value: DecimalAmount): bigint | null {
  const match = /^([+-]?)(\d+)(?:\.(\d*))?(?:e([+-]?\d+))?$/i.exec(
    String(value).trim(),
  );
  if (!match) return null;
  const [, sign, whole, fraction = "", exponentText = "0"] = match;
  const exponent = parseInt(exponentText, 10);
  if (Math.abs(exponent) > 1000) return null;
  const scale = fraction.length - exponent;
  const digits = BigInt(whole + fraction);
  let cents: bigint;
  if (scale <= 2) {
    cents = digits * 10n ** BigInt(2 - scale);
  } else {
    const divisor = 10n ** BigInt(scale - 2);
    cents = digits / divisor + ((digits % divisor) * 2n >= divisor ? 1n : 0n);
  }
  return sign === "-" ? -cents : cents;
}

function formatMinorUnits(cents: bigint): string {
  const absolute = cents < 0n ? -cents : cents;
  const whole = (absolute / 100n).toString();
  const fraction = (absolute % 100n).toString().padStart(2, "0");
  const prefix = whole.slice(0, -3);
  const grouped = prefix
    ? `${prefix.replace(/\B(?=(\d{2})+(?!\d))/g, ",")},${whole.slice(-3)}`
    : whole;
  return `${cents < 0n ? "-" : ""}${grouped}.${fraction}`;
}

export const money = (value: DecimalAmount): string => {
  const cents = minorUnits(value);
  return cents === null ? "—" : formatMinorUnits(cents);
};

export const moneySum = (...values: DecimalAmount[]): string => {
  let total = 0n;
  for (const value of values) {
    const cents = minorUnits(value);
    if (cents === null) return "—";
    total += cents;
  }
  return formatMinorUnits(total);
};

export const human = (value: string) =>
  value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());

export const adjustment = (value: DecimalAmount): string => {
  const cents = minorUnits(value);
  return cents === null
    ? "—"
    : `${cents >= 0n ? "+" : ""}${formatMinorUnits(cents)}`;
};
