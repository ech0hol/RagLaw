export function confirmIrreversibleDelete(label: string): boolean {
  return window.confirm(`确定删除「${label}」？此操作不可恢复。`);
}

export function confirmIrreversibleBatchDelete(count: number, noun = '条记录'): boolean {
  return window.confirm(`确定删除选中的 ${count} ${noun}？此操作不可恢复。`);
}
