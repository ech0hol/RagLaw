import { useRef, useState, type DragEvent, type ReactNode } from 'react';
import { FileText } from 'lucide-react';

type UploadWorkbenchProps = {
  title: string;
  subtitle?: string;
  accept: string;
  formatHint: string;
  submitLabel: string;
  loading?: boolean;
  loadingLabel?: string;
  disabled?: boolean;
  multiple?: boolean;
  file?: File | null;
  files?: File[];
  onFileChange?: (file: File | null) => void;
  onFilesChange?: (files: File[]) => void;
  onSubmit: () => void;
  footerSlot?: ReactNode;
  message?: string | null;
  error?: string | null;
};

export function UploadWorkbench({
  title,
  subtitle,
  accept,
  formatHint,
  submitLabel,
  loading = false,
  loadingLabel = '处理中…',
  disabled = false,
  multiple = false,
  file = null,
  files = [],
  onFileChange,
  onFilesChange,
  onSubmit,
  footerSlot,
  message,
  error,
}: UploadWorkbenchProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const selectedFiles = multiple ? files : (file ? [file] : []);

  function pickFile() {
    inputRef.current?.click();
  }

  function onInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(e.target.files ?? []);
    if (multiple) {
      onFilesChange?.(mergeFiles(files, picked));
      e.target.value = '';
      return;
    }
    onFileChange?.(picked[0] ?? null);
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
    const dropped = Array.from(e.dataTransfer.files ?? []);
    if (dropped.length === 0) {
      return;
    }
    if (multiple) {
      onFilesChange?.(mergeFiles(files, dropped));
      return;
    }
    onFileChange?.(dropped[0] ?? null);
  }

  const canSubmit = selectedFiles.length > 0 && !loading && !disabled;

  return (
    <div className="rl-upload-workbench">
      <h1 className="rl-upload-workbench__title">{title}</h1>
      {subtitle && <p className="rl-upload-workbench__subtitle">{subtitle}</p>}

      <div
        className={[
          'rl-upload-workbench__zone',
          dragOver && 'rl-upload-workbench__zone--drag',
          selectedFiles.length > 0 && 'rl-upload-workbench__zone--has-file',
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
          multiple={multiple}
          onChange={onInputChange}
        />
        {selectedFiles.length > 0 ? (
          <div className="rl-upload-workbench__file">
            <FileText size={22} className="rl-upload-workbench__file-icon" aria-hidden="true" />
            {multiple ? (
              <>
                <span className="rl-upload-workbench__file-name">已选择 {selectedFiles.length} 个文件</span>
                <ul className="rl-upload-workbench__file-list">
                  {selectedFiles.map((item) => (
                    <li key={`${item.name}-${item.size}`}>{item.name}</li>
                  ))}
                </ul>
              </>
            ) : (
              <>
                <span className="rl-upload-workbench__file-name">{selectedFiles[0].name}</span>
                <span className="rl-upload-workbench__file-hint">点击或拖拽可更换文件</span>
              </>
            )}
          </div>
        ) : (
          <>
            <p className="rl-upload-workbench__prompt">点击上传，或将文件拖拽到这里</p>
            <p className="rl-upload-workbench__formats">{formatHint}</p>
          </>
        )}
        {footerSlot && <div className="rl-upload-workbench__footer-slot">{footerSlot}</div>}
      </div>

      {message && <p className="rl-form-hint">{message}</p>}
      {error && <p className="rl-form-error">{error}</p>}

      <button
        type="button"
        className={['rl-upload-workbench__submit', canSubmit && 'rl-upload-workbench__submit--active']
          .filter(Boolean)
          .join(' ')}
        disabled={!canSubmit}
        onClick={onSubmit}
      >
        {loading ? loadingLabel : submitLabel}
      </button>
    </div>
  );
}

function mergeFiles(existing: File[], incoming: File[]): File[] {
  const merged = [...existing];
  for (const file of incoming) {
    const duplicate = merged.some((item) => item.name === file.name && item.size === file.size);
    if (!duplicate) {
      merged.push(file);
    }
  }
  return merged;
}
