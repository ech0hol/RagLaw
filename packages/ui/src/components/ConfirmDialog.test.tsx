import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DIALOG_DISMISS_GRACE_MS } from '../lib/dialogDismiss';
import { ConfirmDialog } from './ConfirmDialog';

function renderDialog(onCancel = vi.fn()) {
  const onConfirm = vi.fn();
  render(
    <ConfirmDialog
      open
      title="删除合同"
      description="确认删除？"
      onConfirm={onConfirm}
      onCancel={onCancel}
    />,
  );
  return { onCancel, onConfirm };
}

function getOverlay() {
  return document.querySelector('.rl-dialog-overlay') as HTMLElement;
}

function getDialogContent() {
  return screen.getByRole('alertdialog');
}

afterEach(() => {
  cleanup();
  document.body.style.overflow = '';
});

describe('ConfirmDialog dismiss guards', () => {
  beforeEach(() => {
    vi.spyOn(performance, 'now').mockReturnValue(1000);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('does not cancel when mousedown starts on content and click lands on backdrop', () => {
    const onCancel = vi.fn();
    renderDialog(onCancel);
    const overlay = getOverlay();
    const content = getDialogContent();

    fireEvent.mouseDown(content);
    fireEvent.click(overlay);

    expect(onCancel).not.toHaveBeenCalled();
  });

  it('cancels when mousedown and click are both on backdrop after grace period', () => {
    const onCancel = vi.fn();
    renderDialog(onCancel);
    const overlay = getOverlay();

    vi.spyOn(performance, 'now').mockReturnValue(1000 + DIALOG_DISMISS_GRACE_MS);
    fireEvent.mouseDown(overlay);
    fireEvent.click(overlay);

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('does not cancel backdrop click inside grace period', () => {
    const onCancel = vi.fn();
    renderDialog(onCancel);
    const overlay = getOverlay();

    vi.spyOn(performance, 'now').mockReturnValue(1000 + 50);
    fireEvent.mouseDown(overlay);
    fireEvent.click(overlay);

    expect(onCancel).not.toHaveBeenCalled();
  });

  it('does not cancel Escape inside grace period', () => {
    const onCancel = vi.fn();
    renderDialog(onCancel);

    vi.spyOn(performance, 'now').mockReturnValue(1000 + 50);
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onCancel).not.toHaveBeenCalled();
  });

  it('cancels Escape after grace period', () => {
    const onCancel = vi.fn();
    renderDialog(onCancel);

    vi.spyOn(performance, 'now').mockReturnValue(1000 + DIALOG_DISMISS_GRACE_MS);
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('does not cancel backdrop click immediately after open with real timestamps', () => {
    vi.restoreAllMocks();
    const onCancel = vi.fn();
    renderDialog(onCancel);
    const overlay = getOverlay();

    fireEvent.mouseDown(overlay);
    fireEvent.click(overlay);

    expect(onCancel).not.toHaveBeenCalled();
  });
});
