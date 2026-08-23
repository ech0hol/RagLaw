import { useEffect } from 'react';
import { createPortal } from 'react-dom';

type ConfirmDialogProps = {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'default' | 'danger';
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = '确认',
  cancelLabel = '取消',
  variant = 'default',
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && !loading) onCancel();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, loading, onCancel]);

  if (!open) return null;

  return createPortal(
    <div className="rl-dialog-overlay" onClick={() => !loading && onCancel()}>
      <div
        className="rl-dialog rl-dialog--enter"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="rl-dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="rl-dialog-title" className="rl-dialog__title">{title}</h2>
        {description && <p className="rl-dialog__description">{description}</p>}
        <div className="rl-dialog__actions">
          <button type="button" className="rl-btn" disabled={loading} onClick={onCancel}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={['rl-btn', variant === 'danger' ? 'rl-btn--danger' : 'rl-btn--primary'].join(' ')}
            disabled={loading}
            onClick={onConfirm}
          >
            {loading ? '处理中…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
