import type { ChatReference } from '../components/ReferenceList';

export const CATALOG_SUMMARY_CHUNK_ID = 'catalog-summary';

export function isCatalogReference(chunkId?: string) {
  return Boolean(chunkId?.startsWith('catalog-'));
}

export function filterUserVisibleReferences(references: ChatReference[]): ChatReference[] {
  return references.filter((ref) => ref.chunkId !== CATALOG_SUMMARY_CHUNK_ID);
}

export function hasCatalogDocuments(references: ChatReference[]) {
  return filterUserVisibleReferences(references).some(
    (ref) => ref.source !== 'web' && isCatalogReference(ref.chunkId),
  );
}
