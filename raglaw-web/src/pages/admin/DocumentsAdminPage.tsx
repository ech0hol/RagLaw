import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { MainHeader, Spinner } from '@raglaw/ui';
import { CategoryTreeNav } from '../../components/CategoryTreeNav';
import { DocumentUploadDialog } from '../../components/DocumentUploadDialog';
import { KnowledgeResultCard } from '../../components/KnowledgeResultCard';
import {
  api,
  deleteAdminDocument,
  fetchAdminDocumentCategoryCounts,
  fetchAdminDocuments,
  reindexAdminDocumentsSelected,
  uploadDocumentsBatch,
  type AdminDocumentRow,
} from '../../lib/api';
import { confirmIrreversibleDelete } from '../../lib/confirmDelete';
import {
  filterKnowledgeCategoryTree,
  findCategoryById,
  findSelectionForCategoryId,
  findSelectionForPath,
  flattenEnabledL3,
  listL1Nodes,
  listL2Nodes,
  listL3Nodes,
  resolveCategoryPath,
  toCategoryOptions,
  type CategoryNode,
} from '../../lib/categories';
import { formatLocalDate } from '../../lib/formatDate';

const POLLING_STAGES = new Set(['PENDING', 'PARSING', 'PARSED', 'INDEXING']);
const PAGE_SIZE = 20;
const MAX_BATCH_REINDEX = 50;

function isReindexSelectable(doc: AdminDocumentRow) {
  return doc.docType !== 'CONTRACT';
}

export function DocumentsAdminPage() {
  const [tree, setTree] = useState<CategoryNode[]>([]);
  const [countById, setCountById] = useState<Map<string, number>>(new Map());
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [documents, setDocuments] = useState<AdminDocumentRow[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [listLoading, setListLoading] = useState(true);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [l1Id, setL1Id] = useState('');
  const [l2Id, setL2Id] = useState('');
  const [l3Id, setL3Id] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pollingIds, setPollingIds] = useState<Set<string>>(new Set());
  const pollingIdsRef = useRef(pollingIds);
  pollingIdsRef.current = pollingIds;
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const pollTimer = useRef<number | null>(null);

  const l3Categories = useMemo(() => flattenEnabledL3(tree), [tree]);
  const enabledTree = useMemo(() => filterKnowledgeCategoryTree(tree), [tree]);
  const l1Nodes = useMemo(() => listL1Nodes(tree), [tree]);
  const selectedL1 = useMemo(() => findCategoryById(enabledTree, l1Id), [enabledTree, l1Id]);
  const selectedL2 = useMemo(() => findCategoryById(enabledTree, l2Id), [enabledTree, l2Id]);
  const l1Options = useMemo(() => toCategoryOptions(l1Nodes), [l1Nodes]);
  const l2Options = useMemo(() => toCategoryOptions(listL2Nodes(selectedL1)), [selectedL1]);
  const l3Options = useMemo(() => toCategoryOptions(listL3Nodes(selectedL2)), [selectedL2]);
  const categoryId = useMemo(
    () => resolveCategoryPath(tree, l1Id, l2Id, l3Id),
    [tree, l1Id, l2Id, l3Id],
  );

  const applyCategorySelection = useCallback((selection: Partial<{ l1Id: string; l2Id: string; l3Id: string }>) => {
    setL1Id(selection.l1Id ?? '');
    setL2Id(selection.l2Id ?? '');
    setL3Id(selection.l3Id ?? '');
  }, []);

  const categoryPathById = useMemo(() => {
    const map = new Map<string, string>();
    for (const cat of l3Categories) {
      map.set(cat.id, cat.path);
    }
    return map;
  }, [l3Categories]);

  const totalCount = useMemo(
    () => Array.from(countById.values()).reduce((sum, n) => sum + n, 0),
    [countById],
  );

  const selectableOnPage = useMemo(
    () => documents.filter(isReindexSelectable),
    [documents],
  );

  const allPageSelected = selectableOnPage.length > 0
    && selectableOnPage.every((doc) => selectedIds.has(doc.id));

  const loadCounts = useCallback(async () => {
    const res = await fetchAdminDocumentCategoryCounts();
    if (res.success) {
      const map = new Map<string, number>();
      for (const row of res.data) {
        map.set(row.categoryId, row.count);
      }
      setCountById(map);
    }
  }, []);

  const loadDocuments = useCallback(async (path: string | null, nextPage = 0) => {
    setListLoading(true);
    const res = await fetchAdminDocuments({
      categoryPath: path ?? undefined,
      page: nextPage,
      pageSize: PAGE_SIZE,
    });
    if (res.success) {
      setDocuments(res.data.items);
      setPage(res.data.page);
      setTotal(res.data.total);
    }
    setListLoading(false);
  }, []);

  useEffect(() => {
    void api<CategoryNode[]>('/api/v1/categories/tree').then((res) => {
      if (res.success) {
        setTree(res.data);
        const l3 = flattenEnabledL3(res.data);
        if (l3[0]) {
          const selection = findSelectionForCategoryId(res.data, l3[0].id);
          if (selection) {
            applyCategorySelection(selection);
          }
        }
      }
    });
    void loadCounts();
    void loadDocuments(null, 0);
  }, [applyCategorySelection, loadCounts, loadDocuments]);

  useEffect(() => {
    if (!selectedPath || tree.length === 0) {
      return;
    }
    applyCategorySelection(findSelectionForPath(tree, selectedPath));
  }, [selectedPath, tree, applyCategorySelection]);

  const pollDocuments = useCallback(async () => {
    const ids = Array.from(pollingIdsRef.current);
    if (ids.length === 0) {
      return;
    }
    const stillPolling = new Set<string>();
    for (const documentId of ids) {
      const res = await api<AdminDocumentRow>(`/api/v1/admin/documents/${documentId}`);
      if (!res.success) {
        stillPolling.add(documentId);
        continue;
      }
      setDocuments((prev) => prev.map((doc) => (doc.id === documentId ? res.data : doc)));
      if (res.data.ingestStage && POLLING_STAGES.has(res.data.ingestStage)) {
        stillPolling.add(documentId);
      }
    }
    setPollingIds((prev) => {
      const next = new Set(prev);
      for (const documentId of ids) {
        if (!stillPolling.has(documentId)) {
          next.delete(documentId);
        }
      }
      if (prev.size > 0 && next.size === 0) {
        const finishedCount = ids.length - stillPolling.size;
        setMessage(`入库完成：${finishedCount > 0 ? finishedCount : prev.size} 个文档`);
        void loadCounts();
        void loadDocuments(selectedPath, page);
      }
      return next;
    });
  }, [loadCounts, loadDocuments, page, selectedPath]);

  useEffect(() => {
    if (pollingIds.size === 0) {
      if (pollTimer.current) {
        window.clearInterval(pollTimer.current);
        pollTimer.current = null;
      }
      return;
    }
    void pollDocuments();
    pollTimer.current = window.setInterval(() => {
      void pollDocuments();
    }, 2000);
    return () => {
      if (pollTimer.current) {
        window.clearInterval(pollTimer.current);
        pollTimer.current = null;
      }
    };
  }, [pollingIds.size, pollDocuments]);

  function onSelectPath(path: string | null) {
    setSelectedPath(path);
    setSelectedIds(new Set());
    void loadDocuments(path, 0);
    if (path) {
      applyCategorySelection(findSelectionForPath(tree, path));
    }
  }

  function openUploadDialog() {
    if (selectedPath) {
      applyCategorySelection(findSelectionForPath(tree, selectedPath));
    }
    setUploadOpen(true);
  }

  function onL1Change(nextL1Id: string) {
    setL1Id(nextL1Id);
    setL2Id('');
    setL3Id('');
  }

  function onL2Change(nextL2Id: string) {
    setL2Id(nextL2Id);
    setL3Id('');
  }

  async function startUpload() {
    if (files.length === 0 || !categoryId) return;
    setUploading(true);
    setMessage(null);
    setError(null);
    try {
      const upload = await uploadDocumentsBatch(categoryId, files);
      if (!upload.success) {
        throw new Error(upload.error?.message ?? '上传失败');
      }
      const asyncMode = upload.data.ingestMode === 'ASYNC';
      const titles = upload.data.items.map((item) => item.title).join('、');
      const pendingIds = upload.data.items
        .filter((item) => item.ingestStage && POLLING_STAGES.has(item.ingestStage))
        .map((item) => item.id);
      if (pendingIds.length > 0) {
        setPollingIds(new Set(pendingIds));
      }
      setMessage(asyncMode
        ? `已上传 ${upload.data.items.length} 个文档（${titles}），后台入库中…`
        : pendingIds.length > 0
          ? `已上传 ${upload.data.items.length} 个文档（${titles}），正在入库…`
          : `已入库 ${upload.data.items.length} 个文档（${titles}）`);
      setFiles([]);
      setUploadOpen(false);
      void loadCounts();
      void loadDocuments(selectedPath, 0);
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setUploading(false);
    }
  }

  async function retryIngest(documentId: string) {
    setError(null);
    const res = await api<AdminDocumentRow>(`/api/v1/admin/documents/${documentId}/retry-ingest`, { method: 'POST' });
    if (!res.success) {
      setError(res.error?.message ?? '重试失败');
      return;
    }
    setPollingIds((prev) => new Set(prev).add(documentId));
    setMessage(`已重新提交入库：${res.data.title}`);
    void loadDocuments(selectedPath, page);
  }

  async function reindexOne(documentId: string) {
    setError(null);
    setMessage('正在重建索引…');
    const res = await api<AdminDocumentRow>(`/api/v1/admin/documents/${documentId}/reindex`, { method: 'POST' });
    if (!res.success) {
      setError(res.error?.message ?? '重建索引失败');
      return;
    }
    setMessage(`已重建索引：${res.data.title}`);
    void loadDocuments(selectedPath, page);
  }

  function toggleSelection(documentId: string, checked: boolean) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        if (next.size >= MAX_BATCH_REINDEX && !next.has(documentId)) {
          setError(`最多勾选 ${MAX_BATCH_REINDEX} 篇文档`);
          return prev;
        }
        next.add(documentId);
      } else {
        next.delete(documentId);
      }
      return next;
    });
  }

  function selectAllOnPage() {
    setError(null);
    const ids = selectableOnPage.map((doc) => doc.id);
    if (ids.length > MAX_BATCH_REINDEX) {
      setError(`本页可重建文档超过 ${MAX_BATCH_REINDEX} 篇，已仅勾选前 ${MAX_BATCH_REINDEX} 篇`);
      setSelectedIds(new Set(ids.slice(0, MAX_BATCH_REINDEX)));
      return;
    }
    setSelectedIds(new Set(ids));
  }

  function clearSelection() {
    setSelectedIds(new Set());
  }

  async function reindexSelected() {
    if (selectedIds.size === 0) return;
    setError(null);
    setMessage('正在批量重建索引…');
    const res = await reindexAdminDocumentsSelected(Array.from(selectedIds));
    if (!res.success) {
      setError(res.error?.message ?? '批量重建失败');
      return;
    }
    setMessage(`批量重建完成：成功 ${res.data.succeeded}/${res.data.total}，失败 ${res.data.failed}`);
    clearSelection();
    void loadDocuments(selectedPath, page);
  }

  async function removeDocument(documentId: string, title: string) {
    if (!confirmIrreversibleDelete(title)) return;
    setError(null);
    const res = await deleteAdminDocument(documentId);
    if (!res.success) {
      setError(res.error?.message ?? '删除失败');
      return;
    }
    setMessage(`已删除：${title}`);
    void loadCounts();
    void loadDocuments(selectedPath, page);
  }

  function buildDocumentMeta(doc: AdminDocumentRow): string[] {
    const meta: string[] = [];
    const categoryPath = categoryPathById.get(doc.categoryId);
    if (categoryPath) {
      meta.push(categoryPath);
    }
    meta.push(`状态 ${doc.status}`);
    if (doc.ingestStage) {
      meta.push(`阶段 ${doc.ingestStage}`);
    }
    if (pollingIds.has(doc.id) && doc.ingestStage && POLLING_STAGES.has(doc.ingestStage)) {
      meta.push('入库中…');
    }
    if (doc.ingestError) {
      meta.push(`错误 ${doc.ingestError}`);
    }
    meta.push(`入库 ${formatLocalDate(doc.createdAt)}`);
    meta.push(`生效 ${doc.effectiveDate ? formatLocalDate(doc.effectiveDate) : '未识别'}`);
    return meta;
  }

  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <div className="rl-doc-admin-page">
      <MainHeader
        title="文档管理"
        actions={
          <button type="button" className="rl-btn rl-btn--primary" onClick={openUploadDialog}>
            上传文档
          </button>
        }
      />

      {message && <p className="rl-form-hint rl-doc-admin-page__message">{message}</p>}
      {error && <p className="rl-form-error rl-doc-admin-page__message">{error}</p>}

      <div className="rl-doc-admin-layout">
        <CategoryTreeNav
          tree={enabledTree}
          countById={countById}
          selectedPath={selectedPath}
          totalCount={totalCount}
          onSelect={onSelectPath}
        />

        <section className="rl-doc-admin-content">
          <div className="rl-doc-admin-toolbar">
            <div className="rl-doc-admin-toolbar__left">
              {selectableOnPage.length > 0 && (
                <label className="rl-checkbox-item">
                  <input
                    type="checkbox"
                    checked={allPageSelected}
                    onChange={(e) => {
                      if (e.target.checked) {
                        selectAllOnPage();
                      } else {
                        clearSelection();
                      }
                    }}
                  />
                  全选本页
                </label>
              )}
              <p className="rl-text-muted">共 {total} 篇文档</p>
            </div>
            <button
              type="button"
              className="rl-btn rl-btn--sm"
              disabled={selectedIds.size === 0}
              onClick={() => void reindexSelected()}
            >
              批量重建选中 ({selectedIds.size}/{MAX_BATCH_REINDEX})
            </button>
          </div>

          {listLoading && <Spinner />}

          {!listLoading && documents.length === 0 && (
            <p className="rl-text-muted">当前目录下暂无文档。</p>
          )}

          {!listLoading && documents.length > 0 && (
            <div className="rl-knowledge-result-list">
              {documents.map((doc) => (
                <KnowledgeResultCard
                  key={doc.id}
                  documentId={doc.id}
                  title={doc.title}
                  meta={buildDocumentMeta(doc)}
                  linkFrom="admin"
                  showViewOriginalLink={doc.docType !== 'CONTRACT'}
                  leading={isReindexSelectable(doc) ? (
                    <input
                      type="checkbox"
                      aria-label={`选择 ${doc.title}`}
                      checked={selectedIds.has(doc.id)}
                      onChange={(e) => toggleSelection(doc.id, e.target.checked)}
                    />
                  ) : undefined}
                  actions={
                    <>
                      {doc.ingestStage === 'FAILED' && (
                        <button
                          type="button"
                          className="rl-btn rl-btn--sm"
                          onClick={() => void retryIngest(doc.id)}
                        >
                          重试入库
                        </button>
                      )}
                      {doc.docType !== 'CONTRACT' && (
                        <button
                          type="button"
                          className="rl-btn rl-btn--sm"
                          onClick={() => void reindexOne(doc.id)}
                        >
                          重建索引
                        </button>
                      )}
                    </>
                  }
                  trailingActions={doc.docType !== 'CONTRACT' ? (
                    <button
                      type="button"
                      className="rl-btn rl-btn--sm rl-btn--danger"
                      onClick={() => void removeDocument(doc.id, doc.title)}
                    >
                      删除
                    </button>
                  ) : undefined}
                />
              ))}
            </div>
          )}

          {totalPages > 1 && (
            <div className="rl-pagination">
              <button
                type="button"
                className="rl-btn"
                disabled={page <= 0 || listLoading}
                onClick={() => {
                  setSelectedIds(new Set());
                  void loadDocuments(selectedPath, page - 1);
                }}
              >
                上一页
              </button>
              <span className="rl-text-muted">
                第 {page + 1} / {totalPages} 页
              </span>
              <button
                type="button"
                className="rl-btn"
                disabled={page + 1 >= totalPages || listLoading}
                onClick={() => {
                  setSelectedIds(new Set());
                  void loadDocuments(selectedPath, page + 1);
                }}
              >
                下一页
              </button>
            </div>
          )}
        </section>
      </div>

      <DocumentUploadDialog
        open={uploadOpen}
        onClose={() => {
          if (!uploading) {
            setUploadOpen(false);
            setError(null);
          }
        }}
        l1Options={l1Options}
        l2Options={l2Options}
        l3Options={l3Options}
        l1Id={l1Id}
        l2Id={l2Id}
        l3Id={l3Id}
        onL1Change={onL1Change}
        onL2Change={onL2Change}
        onL3Change={setL3Id}
        files={files}
        onFilesChange={setFiles}
        uploading={uploading}
        onUpload={() => void startUpload()}
        error={error}
      />
    </div>
  );
}
