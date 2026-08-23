import { Badge } from './Badge';
import { Sparkles } from 'lucide-react';

type PlaceholderPageProps = {
  title: string;
  description?: string;
  phase?: string;
};

export function PlaceholderPage({ title, description, phase }: PlaceholderPageProps) {
  return (
    <div className="rl-placeholder">
      <div className="rl-placeholder__icon-wrap">
        <Sparkles aria-hidden="true" />
      </div>
      {phase && <Badge variant="muted">{phase}</Badge>}
      <h2 className="rl-placeholder__title">{title}</h2>
      {description && <p className="rl-placeholder__desc">{description}</p>}
    </div>
  );
}
