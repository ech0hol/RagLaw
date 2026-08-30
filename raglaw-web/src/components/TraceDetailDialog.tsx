import { FormDialog, Spinner } from '@raglaw/ui';
import { TraceDetailContent } from './TraceDetailContent';
import type { TraceDetail } from './traceDetailTypes';

type TraceDetailDialogProps = {
  open: boolean;
  detail: TraceDetail | null;
  loading: boolean;
  error: string | null;
  deleting?: boolean;
  onClose: () => void;
  onDelete: () => void;
};

export function TraceDetailDialog({
  open,
  detail,
  loading,
  error,
  deleting = false,
  onClose,
  onDelete,
}: TraceDetailDialogProps) {
  const title = detail
    ? `Trace ${detail.trace.id.slice(0, 8)}…`
    : 'Trace 详情';

  return (
    <FormDialog
      open={open}
      title={title}
      onClose={onClose}
      dialogClassName="rl-dialog--trace"
      footer={detail ? (
        <button
          type="button"
          className="rl-btn rl-btn--danger rl-btn--sm"
          disabled={deleting}
          onClick={onDelete}
        >
          {deleting ? '删除中…' : '删除'}
        </button>
      ) : undefined}
    >
      {loading && <Spinner />}
      {error && <p className="rl-form-error">{error}</p>}
      {!loading && detail && <TraceDetailContent detail={detail} />}
    </FormDialog>
  );
}
