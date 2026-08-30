import { describe, expect, it, vi } from 'vitest';
import { DIALOG_DISMISS_GRACE_MS, isDialogDismissAllowed } from './dialogDismiss';

describe('isDialogDismissAllowed', () => {
  it('returns false when openedAt is zero', () => {
    vi.spyOn(performance, 'now').mockReturnValue(50_000);
    expect(isDialogDismissAllowed(0)).toBe(false);
    vi.restoreAllMocks();
  });

  it('returns false inside grace period', () => {
    vi.spyOn(performance, 'now').mockReturnValue(1000);
    expect(isDialogDismissAllowed(700)).toBe(false);
    vi.restoreAllMocks();
  });

  it('returns true after grace period', () => {
    vi.spyOn(performance, 'now').mockReturnValue(1000 + DIALOG_DISMISS_GRACE_MS);
    expect(isDialogDismissAllowed(1000)).toBe(true);
    vi.restoreAllMocks();
  });
});
