import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';

export type SelectOption = {
  value: string;
  label: string;
};

type SelectProps = {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  disabled?: boolean;
  placeholder?: string;
  className?: string;
  menuWidthFromOptions?: boolean;
  labelAlign?: 'left' | 'center';
};

export function Select({
  label,
  value,
  onChange,
  options,
  disabled,
  placeholder,
  className,
  menuWidthFromOptions = false,
  labelAlign = 'left',
}: SelectProps) {
  const [open, setOpen] = useState(false);
  const [measuredWidth, setMeasuredWidth] = useState<number | undefined>();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const measureRef = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => o.value === value);
  const labelClass = labelAlign === 'center' ? 'rl-select-trigger__label--center' : '';
  const optionClass = labelAlign === 'center' ? 'rl-select-option--center' : '';

  useLayoutEffect(() => {
    if (!menuWidthFromOptions || !measureRef.current || !triggerRef.current) {
      setMeasuredWidth(undefined);
      return;
    }
    let max = 0;
    measureRef.current.querySelectorAll('.rl-select-measure__item').forEach((el) => {
      max = Math.max(max, el.scrollWidth);
    });
    const style = getComputedStyle(triggerRef.current);
    const extra =
      parseFloat(style.paddingLeft) +
      parseFloat(style.paddingRight) +
      16 +
      (parseFloat(style.gap) || 8);
    setMeasuredWidth(Math.ceil(max + extra));
  }, [options, menuWidthFromOptions, label]);

  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    function onPointerDown(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('mousedown', onPointerDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('mousedown', onPointerDown);
    };
  }, [open]);

  return (
    <div
      className={['rl-select', className].filter(Boolean).join(' ')}
      ref={rootRef}
      style={measuredWidth ? { width: measuredWidth } : undefined}
    >
      {label && <span className="rl-field__label">{label}</span>}
      {menuWidthFromOptions && (
        <div ref={measureRef} className="rl-select-measure" aria-hidden="true">
          {options.map((option) => (
            <span key={option.value} className="rl-select-measure__item">
              {option.label}
            </span>
          ))}
        </div>
      )}
      <button
        type="button"
        className="rl-select-trigger"
        ref={triggerRef}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <span className={['rl-select-trigger__label', labelClass].filter(Boolean).join(' ')}>
          {selected?.label ?? placeholder ?? '请选择'}
        </span>
        <ChevronDown className="rl-select-trigger__icon" size={16} aria-hidden="true" />
      </button>
      {open && (
        <ul
          className="rl-select-menu rl-select-menu--enter"
          role="listbox"
          onMouseDown={(e) => e.preventDefault()}
        >
          {options.map((option) => (
            <li key={option.value}>
              <button
                type="button"
                role="option"
                aria-selected={option.value === value}
                className={[
                  'rl-select-option',
                  optionClass,
                  option.value === value && 'rl-select-option--active',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={(e) => {
                  e.stopPropagation();
                  onChange(option.value);
                  setOpen(false);
                }}
              >
                {option.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
