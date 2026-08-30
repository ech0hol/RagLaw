const VIRTUAL_PARENT_LABEL = /^段落组\s*\d+$/;

export function buildFullTextFromChunks(
  chunks: { chunkIndex: number; content: string; chunkLevel?: string | null }[],
): string {
  return [...chunks]
    .sort((a, b) => a.chunkIndex - b.chunkIndex)
    .filter((chunk) => {
      if (chunk.chunkLevel === 'PARENT') {
        return false;
      }
      const text = chunk.content?.trim() ?? '';
      if (!text) {
        return false;
      }
      return !VIRTUAL_PARENT_LABEL.test(text);
    })
    .map((chunk) => chunk.content.trim())
    .join('\n\n');
}
