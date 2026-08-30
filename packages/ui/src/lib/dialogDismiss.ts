export const DIALOG_DISMISS_GRACE_MS = 400;

export function isDialogDismissAllowed(openedAtMs: number): boolean {
  if (openedAtMs <= 0) return false;
  return performance.now() - openedAtMs >= DIALOG_DISMISS_GRACE_MS;
}
