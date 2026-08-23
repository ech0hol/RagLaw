import { useRef, useState, type DragEvent, type ReactNode } from 'react';
import { FileText } from 'lucide-react';

type UploadWorkbenchProps = {
  title: string;
  subtitle?: string;
  accept: string;
  formatHint: string;
  submitLabel: string;
  loading?: boolean;
  disabled?: boolean;
  file: File | null;
  onFileChange: (file: File | null) => void;
  onSubmit: () => void;
  footerSlot?: ReactNode;
  error?: string | null;
};

export function UploadWorkbench({
  title,
  subtitle,
  accept,
  formatHint,
  submitLabel,
  loading = false,
  disabled = false,
  file,
  onFileChange,
  onSubmit,
  footerSlot,
  error,
}: UploadWorkbenchProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  function pickFile() {
    inputRef.current?.click();
  }

  function onInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const picked = e.target.files?.[0] ?? null;
    onFileChange(picked);
  }

  function onDragOver(e: DragEvent) {
    e.preventDefault();
    setDragOver(true);
  }

  function onDragLeave(e: DragEvent) {
    e.preventDefault();
    setDragOver(false);
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setDragOver(false);
    const dropped = e.dataTransfer.files?.[0];
    if (dropped) onFileChange(dropped);
  }

  const canSubmit = Boolean(file) && !loading && !disabled;

  return (
    <div className="rl-upload-workbench">
      <h1 className="rl-upload-workbench__title">{title}</h1>
      {subtitle && <p className="rl-upload-workbench__subtitle">{subtitle}</p>}

      <div
        className={[
          'rl-upload-workbench__zone',
          dragOver && 'rl-upload-workbench__zone--drag',
          file && 'rl-upload-workbench__zone--has-file',
        ]
          .filter(Boolean)
          .join(' ')}
        onClick={pickFile}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            pickFile();
          }
        }}
      >
        <input
          ref={inputRef}
          type="file"
          className="rl-upload-workbench__input"
          accept={accept}
          onChange={onInputChange}
        />
        {file ? (
          <div className="rl-upload-workbench__file">
            <FileText size={22} className="rl-upload-workbench__file-icon" aria-hidden="true" />
            <span className="rl-upload-workbench__file-name">{file.name}</span>
            <span className="rl-upload-workbench__file-hint">点击或拖拽可更换文件</span>
          </div>
        ) : (
          <>
            <p className="rl-upload-workbench__prompt">点击上传，或将文件拖拽到这里</p>
            <p className="rl-upload-workbench__formats">{formatHint}</p>
          </>
        )}
        {footerSlot && <div className="rl-upload-workbench__footer-slot">{footerSlot}</div>}
      </div>

      {error && <p className="rl-form-error">{error}</p>}

      <button
        type="button"
        className={['rl-upload-workbench__submit', canSubmit && 'rl-upload-workbench__submit--active']
          .filter(Boolean)
          .join(' ')}
        disabled={!canSubmit}
        onClick={onSubmit}
      >
        {loading ? '处理中…' : submitLabel}
      </button>
    </div>
  );
}
