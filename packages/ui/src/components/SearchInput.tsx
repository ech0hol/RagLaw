import type { InputHTMLAttributes } from 'react';
import { Search } from 'lucide-react';

type SearchInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  showShortcutHint?: boolean;
};

export function SearchInput({ showShortcutHint = true, className, ...props }: SearchInputProps) {
  return (
    <div className={['rl-search', className].filter(Boolean).join(' ')}>
      <Search className="rl-search__icon" aria-hidden="true" />
      <input type="search" className="rl-search__input" {...props} />
      {showShortcutHint && <span className="rl-search__hint">⌘K</span>}
    </div>
  );
}
