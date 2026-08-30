export function mergeUploadFiles(existing: File[], incoming: File[]): File[] {
  const merged = [...existing];
  for (const file of incoming) {
    const duplicate = merged.some((item) => item.name === file.name && item.size === file.size);
    if (!duplicate) {
      merged.push(file);
    }
  }
  return merged;
}
