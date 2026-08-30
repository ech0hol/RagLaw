import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { MAIN_OVERLAY_ROOT_ID } from '../layout/constants';

const PANEL_CLOSE_MS = 280;

type SlidePanelProps = {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  side?: 'left' | 'right';
  anchor?: 'viewport' | 'sidebar';
  width?: string;
};

function getPortalTarget(anchor: 'viewport' | 'sidebar'): HTMLElement {
  if (anchor === 'sidebar') {
    return document.getElementById(MAIN_OVERLAY_ROOT_ID) ?? document.body;
  }
  return document.body;
}

export function SlidePanel({
  open,
  onClose,
  title,
  children,
  side = 'left',
  anchor = 'viewport',
  width = '50vw',
}: SlidePanelProps) {
  const [mounted, setMounted] = useState(open);
  const [visible, setVisible] = useState(false);
  const panelRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (open) {
      setMounted(true);
      return;
    }
    setVisible(false);
    const timer = window.setTimeout(() => setMounted(false), PANEL_CLOSE_MS);
    return () => window.clearTimeout(timer);
  }, [open]);

  useLayoutEffect(() => {
    if (!mounted || !open) return;
    setVisible(false);
    void panelRef.current?.getBoundingClientRect();
    const frame = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(frame);
  }, [mounted, open]);

  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!mounted) return null;

  const portalTarget = getPortalTarget(anchor);
  const anchoredToMain = anchor === 'sidebar' && portalTarget.id === MAIN_OVERLAY_ROOT_ID;

  const rootClass = [
    'rl-slide-panel-root',
    anchoredToMain && 'rl-slide-panel-root--main',
    visible && 'rl-slide-panel-root--visible',
    !open && mounted && 'rl-slide-panel-root--closing',
    anchor === 'sidebar' && !anchoredToMain && 'rl-slide-panel-root--sidebar-fallback',
  ]
    .filter(Boolean)
    .join(' ');

  const panelClass = [
    'rl-slide-panel',
    `rl-slide-panel--${side}`,
    visible && 'rl-slide-panel--open',
    anchoredToMain && 'rl-slide-panel--inset',
  ]
    .filter(Boolean)
    .join(' ');

  return createPortal(
    <div className={rootClass}>
      <div className="rl-slide-panel__backdrop" onClick={onClose} />
      <aside
        ref={panelRef}
        className={panelClass}
        style={{ width, maxWidth: '640px' }}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header className="rl-slide-panel__header">
          <h2 className="rl-slide-panel__title">{title}</h2>
          <button type="button" className="rl-slide-panel__close" onClick={onClose} aria-label="关闭">
            <X size={18} />
          </button>
        </header>
        <div className="rl-slide-panel__body">{children}</div>
      </aside>
    </div>,
    portalTarget,
  );
}
