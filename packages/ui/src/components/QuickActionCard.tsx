import type { ReactNode } from 'react';

type PastelColor = 'yellow' | 'blue' | 'green' | 'pink';

type QuickActionCardProps = {
  icon: ReactNode;
  color: PastelColor;
  children: ReactNode;
  onClick?: () => void;
  delayIndex?: number;
};

export function QuickActionCard({ icon, color, children, onClick, delayIndex }: QuickActionCardProps) {
  return (
    <button
      type="button"
      className="rl-quick-action-card"
      onClick={onClick}
      style={delayIndex !== undefined ? { animationDelay: `${delayIndex * 50}ms` } : undefined}
    >
      <span className={`rl-quick-action-card__icon rl-quick-action-card__icon--${color}`}>{icon}</span>
      <span className="rl-quick-action-card__text">{children}</span>
    </button>
  );
}
