import { useRef } from 'react';
import { FileText } from 'lucide-react';
import { SlidePanel, Select } from '@raglaw/ui';
import { mergeUploadFiles } from '../lib/mergeUploadFiles';
import type { CategoryOption } from '../lib/categories';

type DocumentUploadDialogProps = {
  open: boolean;
  onClose: () => void;
  l1Options: CategoryOption[];
  l2Options: CategoryOption[];
  l3Options: CategoryOption[];
  l1Id: string;
  l2Id: string;
  l3Id: string;
  onL1Change: (id: string) => void;
  onL2Change: (id: string) => void;
  onL3Change: (id: string) => void;
  files: File[];
  onFilesChange: (files: File[]) => void;
  uploading: boolean;
  onUpload: () => void;
  error: string | null;
};

export function DocumentUploadDialog({
  open,
  onClose,
  l1Options,
  l2Options,
  l3Options,
  l1Id,
  l2Id,
  l3Id,
  onL1Change,
  onL2Change,
  onL3Change,
  files,
  onFilesChange,
  uploading,
  onUpload,
  error,
}: DocumentUploadDialogProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <SlidePanel open={open} onClose={onClose} title="上传文档" side="right" width="420px">
      <div className="rl-doc-upload-dialog">
        <p className="rl-text-muted">
          上传法规/案例文档；危化品、行政监管类法规请归入行政法类目。扫描版 PDF 或图片需配置 OCR。
        </p>
        <div
          className={['rl-doc-upload-dialog__zone', files.length > 0 && 'rl-doc-upload-dialog__zone--has-file'].filter(Boolean).join(' ')}
          onClick={() => inputRef.current?.click()}
        >
          <FileText size={28} aria-hidden="true" />
          <p>{files.length > 0 ? `已选择 ${files.length} 个文件` : '点击选择文件'}</p>
          {files.length > 0 && (
            <ul className="rl-doc-upload-dialog__file-list">
              {files.map((file) => (
                <li key={`${file.name}-${file.size}`}>{file.name}</li>
              ))}
            </ul>
          )}
          <p className="rl-text-muted">支持 doc、docx、pdf、md、txt、jpg、png，可多选</p>
          <input
            ref={inputRef}
            type="file"
            accept=".pdf,.doc,.docx,.md,.txt,.jpg,.jpeg,.png"
            multiple
            hidden
            onChange={(e) => {
              const picked = Array.from(e.target.files ?? []);
              onFilesChange(mergeUploadFiles(files, picked));
              e.target.value = '';
            }}
          />
        </div>
        {l1Options.length > 0 && (
          <>
            <div className="rl-doc-upload-dialog__field">
              <span>一级类目</span>
              <Select value={l1Id} onChange={onL1Change} options={l1Options} />
            </div>
            <div className="rl-doc-upload-dialog__field">
              <span>二级类目</span>
              <Select
                value={l2Id}
                onChange={onL2Change}
                options={l2Options}
                disabled={!l1Id}
                placeholder={l1Id ? '请选择二级类目' : '请先选择上级类目'}
              />
            </div>
            <div className="rl-doc-upload-dialog__field">
              <span>三级类目</span>
              <Select
                value={l3Id}
                onChange={onL3Change}
                options={l3Options}
                disabled={!l2Id}
                placeholder={l2Id ? '请选择三级类目' : '请先选择上级类目'}
              />
            </div>
          </>
        )}
        {error && <p className="rl-form-error">{error}</p>}
        <button
          type="button"
          className="rl-btn rl-btn--primary rl-doc-upload-dialog__submit"
          disabled={files.length === 0 || !l3Id || uploading}
          onClick={onUpload}
        >
          {uploading ? '上传中…' : files.length > 1 ? '上传并入库（批量）' : '上传并入库'}
        </button>
      </div>
    </SlidePanel>
  );
}
