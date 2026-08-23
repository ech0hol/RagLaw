import type { HTMLAttributes, ReactNode } from 'react';

type CardPadding = 'sm' | 'md' | 'lg' | 'none';

type CardProps = HTMLAttributes<HTMLDivElement> & {
  children: ReactNode;
  hover?: boolean;
  interactive?: boolean;
  padding?: CardPadding;
};

export function Card({
  children,
  hover = false,
  interactive = false,
  padding = 'md',
  className,
  ...props
}: CardProps) {
  const classes = [
    'rl-card',
    hover && 'rl-card--hover',
    interactive && 'rl-card--interactive',
    padding !== 'none' && `rl-card--padding-${padding}`,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes} {...props}>
      {children}
    </div>
  );
}
