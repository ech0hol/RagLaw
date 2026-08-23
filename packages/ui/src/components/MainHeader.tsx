import type { ReactNode } from 'react';

type MainHeaderProps = {
  title: string;
  actions?: ReactNode;
};

export function MainHeader({ title, actions }: MainHeaderProps) {
  return (
    <header className="rl-main-header">
      <h1 className="rl-main-header__title">{title}</h1>
      {actions && <div className="rl-main-header__actions">{actions}</div>}
    </header>
  );
}
