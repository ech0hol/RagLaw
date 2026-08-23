import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';

type NavItemProps = {
  to: string;
  children: ReactNode;
  end?: boolean;
  icon?: ReactNode;
};

export function NavItem({ to, children, end, icon }: NavItemProps) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) => ['rl-nav-item', isActive && 'rl-nav-item--active'].filter(Boolean).join(' ')}
    >
      {icon && <span className="rl-nav-item__icon">{icon}</span>}
      <span className="rl-nav-item__label">{children}</span>
    </NavLink>
  );
}

export function NavSection({ children }: { children: ReactNode }) {
  return <div className="rl-nav-section">{children}</div>;
}
