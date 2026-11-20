import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "danger" | "ghost";

const VARIANTS: Record<Variant, string> = {
  primary: "bg-river-700 text-white hover:bg-river-800 disabled:bg-stone-400",
  secondary:
    "bg-white text-river-800 border border-river-700 hover:bg-river-50 disabled:text-stone-400 disabled:border-stone-300",
  danger: "bg-clay-600 text-white hover:bg-red-800 disabled:bg-stone-400",
  ghost: "bg-transparent text-river-800 hover:bg-river-50 disabled:text-stone-400",
};

const BASE =
  "inline-flex min-h-12 items-center justify-center gap-2 rounded-xl px-5 text-base font-semibold transition-colors disabled:cursor-not-allowed";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
  block?: boolean;
}

export function Button({
  variant = "primary",
  loading = false,
  block = false,
  className = "",
  children,
  disabled,
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`${BASE} ${VARIANTS[variant]} ${block ? "w-full" : ""} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? (
        <span
          aria-hidden="true"
          className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      ) : null}
      {children}
    </button>
  );
}

export function ButtonLink({
  href,
  variant = "primary",
  block = false,
  children,
}: {
  href: string;
  variant?: Variant;
  block?: boolean;
  children: ReactNode;
}) {
  return (
    <Link href={href} className={`${BASE} ${VARIANTS[variant]} ${block ? "w-full" : ""}`}>
      {children}
    </Link>
  );
}
