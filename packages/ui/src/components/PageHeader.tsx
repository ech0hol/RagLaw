type PageHeaderProps = {
  title: string;
  subtitle?: string;
};

export function PageHeader({ title, subtitle }: PageHeaderProps) {
  return (
    <header className="rl-page-header">
      <h1 className="rl-page-header__title">{title}</h1>
      {subtitle && <p className="rl-page-header__subtitle">{subtitle}</p>}
    </header>
  );
}
