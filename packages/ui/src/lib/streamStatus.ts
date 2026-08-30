const TOOL_STATUS_HINTS = ['正在检索知识库', '正在联网检索', '正在整理'];

export function isToolStreamStatus(status: string | null | undefined): boolean {
  if (!status) return false;
  return TOOL_STATUS_HINTS.some((hint) => status.includes(hint));
}
