import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";

/**
 * Campos com rótulo visível, dica e erro ligados por `aria-describedby`, e
 * `aria-invalid` quando há erro — leitores de tela anunciam a mensagem.
 */

interface FieldShellProps {
  id: string;
  label: string;
  hint?: string;
  error?: string | null;
  required?: boolean;
  children: ReactNode;
}

const CONTROL =
  "mt-1 block w-full min-h-12 rounded-xl border border-stone-400 bg-white px-3 text-base text-stone-900 aria-[invalid=true]:border-clay-600";

function FieldShell({ id, label, hint, error, required, children }: FieldShellProps) {
  return (
    <div className="mb-4">
      <label htmlFor={id} className="block text-sm font-semibold text-stone-800">
        {label}
        {required ? <span className="text-clay-600"> *</span> : null}
      </label>
      {children}
      {hint ? (
        <p id={`${id}-hint`} className="mt-1 text-sm text-stone-600">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} role="alert" className="mt-1 text-sm font-medium text-clay-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function describedBy(id: string, hint?: string, error?: string | null) {
  const ids = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean);
  return ids.length ? ids.join(" ") : undefined;
}

interface Common {
  label: string;
  hint?: string;
  error?: string | null;
}

export function TextField({
  label,
  hint,
  error,
  required,
  ...props
}: Common & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} required={required}>
      <input
        id={id}
        className={CONTROL}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...props}
      />
    </FieldShell>
  );
}

export function SelectField({
  label,
  hint,
  error,
  required,
  children,
  ...props
}: Common & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} required={required}>
      <select
        id={id}
        className={CONTROL}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...props}
      >
        {children}
      </select>
    </FieldShell>
  );
}

export function TextAreaField({
  label,
  hint,
  error,
  required,
  ...props
}: Common & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} required={required}>
      <textarea
        id={id}
        rows={3}
        className={`${CONTROL} py-2`}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...props}
      />
    </FieldShell>
  );
}

export function CheckboxField({
  label,
  hint,
  ...props
}: Common & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <div className="mb-4 flex min-h-12 items-start gap-3">
      <input
        id={id}
        type="checkbox"
        className="mt-1 size-6 accent-river-700"
        aria-describedby={hint ? `${id}-hint` : undefined}
        {...props}
      />
      <label htmlFor={id} className="text-base text-stone-900">
        {label}
        {hint ? (
          <span id={`${id}-hint`} className="block text-sm text-stone-600">
            {hint}
          </span>
        ) : null}
      </label>
    </div>
  );
}
