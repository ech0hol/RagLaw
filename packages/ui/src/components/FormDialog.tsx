import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { useDialogOverlayDismiss } from '../lib/useDialogOverlayDismiss';

const DIALOG_CLOSE_MS = 220;

type FormDialogProps = {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
  dialogClassName?: string;
};

export function FormDialog({
  open,
  title,
  onClose,
  children,
  footer,
  wide = false,
  dialogClassName,
}: FormDialogProps) {
  const [mounted, setMounted] = useState(open);
  const [visible, setVisible] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);
  const { onBackdropMouseDown, onBackdropClick } = useDialogOverlayDismiss(mounted && open, onClose);

  useEffect(() => {
    if (open) {
      setMounted(true);
      return;
    }
    setVisible(false);
    const timer = window.setTimeout(() => setMounted(false), DIALOG_CLOSE_MS);
    return () => window.clearTimeout(timer);
  }, [open]);

  useLayoutEffect(() => {
    if (!mounted || !open) return;
    setVisible(false);
    void dialogRef.current?.getBoundingClientRect();
    const frame = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(frame);
  }, [mounted, open]);

  useEffect(() => {
    if (!mounted) return;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = '';
    };
  }, [mounted]);

  if (!mounted) return null;

  const overlayClass = [
    'rl-dialog-overlay',
    'rl-form-dialog-root',
    visible && 'rl-form-dialog-root--visible',
  ].filter(Boolean).join(' ');

  const dialogClass = [
    'rl-dialog',
    'rl-dialog--form',
    wide && 'rl-dialog--wide',
    dialogClassName,
    visible && 'rl-dialog--visible',
  ].filter(Boolean).join(' ');

  return createPortal(
    <div
      className={overlayClass}
      onMouseDown={onBackdropMouseDown}
      onClick={onBackdropClick}
    >
      <div
        ref={dialogRef}
        className={dialogClass}
        role="dialog"
        aria-modal="true"
        aria-labelledby="rl-form-dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="rl-form-dialog__header">
          <h2 id="rl-form-dialog-title" className="rl-dialog__title">{title}</h2>
          <button
            type="button"
            className="rl-form-dialog__close"
            aria-label="关闭"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>
        <div className="rl-form-dialog__body">{children}</div>
        {footer && <div className="rl-form-dialog__footer">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}
