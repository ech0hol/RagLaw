import type { ButtonHTMLAttributes } from 'react';

type ButtonVariant = 'primary' | 'ghost' | 'subtle';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

export function Button({ variant = 'primary', className, type = 'button', ...props }: ButtonProps) {
  const classes = ['rl-btn', `rl-btn--${variant}`, className].filter(Boolean).join(' ');
  return <button type={type} className={classes} {...props} />;
}
