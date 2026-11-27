import { useId, useState } from 'react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';

export function Button({
  tone = 'secondary',
  className = '',
  type = 'button',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  tone?: 'primary' | 'secondary' | 'danger' | 'quiet';
}) {
  return (
    <button
      {...props}
      type={type}
      className={`workflowButton workflowButton--${tone} ${className}`}
    />
  );
}

export function PanelHeader({
  title,
  meta,
  children,
}: {
  title: string;
  meta?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="workflowHeader">
      <div>
        <h2>{title}</h2>
        {meta && <div className="workflowMeta">{meta}</div>}
      </div>
      {children && <div className="workflowActions">{children}</div>}
    </div>
  );
}

export function StatusBadge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode;
  tone?: 'neutral' | 'positive' | 'warning' | 'danger';
}) {
  return (
    <span className={`workflowBadge workflowBadge--${tone}`}>{children}</span>
  );
}

export function ErrorMessage({ children }: { children: ReactNode }) {
  return (
    <p className="workflowError" role="alert">
      {children}
    </p>
  );
}

export function SummaryGrid({
  items,
}: {
  items: { label: string; value: ReactNode }[];
}) {
  return (
    <dl className="workflowSummary">
      {items.map(({ label, value }) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: (accessibility: {
    'aria-invalid'?: true;
    'aria-describedby'?: string;
  }) => ReactNode;
}) {
  const id = useId();
  const [invalid, setInvalid] = useState('');
  const feedback = invalid || error;
  return (
    <label
      className="workflowField"
      onInvalid={(event) => {
        const input = event.target;
        if (
          input instanceof HTMLInputElement ||
          input instanceof HTMLSelectElement ||
          input instanceof HTMLTextAreaElement
        ) {
          setInvalid(input.validationMessage);
        }
      }}
      onInput={() => setInvalid('')}
    >
      {label}
      {children(
        feedback ? { 'aria-invalid': true, 'aria-describedby': id } : {},
      )}
      {feedback && (
        <span className="workflowFieldError" id={id} role="alert">
          {feedback}
        </span>
      )}
    </label>
  );
}
