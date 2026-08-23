import type { ReactNode } from 'react';

type BadgeVariant = 'default' | 'muted' | 'success';

type BadgeProps = {
  children: ReactNode;
  variant?: BadgeVariant;
};

export function Badge({ children, variant = 'default' }: BadgeProps) {
  const classes = [
    'rl-badge',
    variant === 'muted' && 'rl-badge--muted',
    variant === 'success' && 'rl-badge--success',
  ]
    .filter(Boolean)
    .join(' ');
  return <span className={classes}>{children}</span>;
}
