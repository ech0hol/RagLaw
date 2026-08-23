import type { InputHTMLAttributes, TextareaHTMLAttributes } from 'react';

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
};

export function Input({ label, className, id, ...props }: InputProps) {
  const inputId = id ?? (label ? `input-${label}` : undefined);
  return (
    <label className="rl-field" htmlFor={inputId}>
      {label && <span className="rl-field__label">{label}</span>}
      <input id={inputId} className={['rl-input', className].filter(Boolean).join(' ')} {...props} />
    </label>
  );
}

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label?: string;
};

export function Textarea({ label, className, id, ...props }: TextareaProps) {
  const inputId = id ?? (label ? `textarea-${label}` : undefined);
  return (
    <label className="rl-field" htmlFor={inputId}>
      {label && <span className="rl-field__label">{label}</span>}
      <textarea id={inputId} className={['rl-textarea', className].filter(Boolean).join(' ')} {...props} />
    </label>
  );
}
