import { useState } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DIALOG_DISMISS_GRACE_MS } from '../lib/dialogDismiss';
import { MAIN_OVERLAY_ROOT_ID } from '../layout/constants';
import { ConfirmDialog } from './ConfirmDialog';
import { SlidePanel } from './SlidePanel';

function HistoryDeleteHarness({
  onCancel,
  onConfirm,
}: {
  onCancel?: () => void;
  onConfirm?: () => void;
}) {
  const [panelOpen, setPanelOpen] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);

  function requestDelete() {
    setPanelOpen(false);
    queueMicrotask(() => setDeleteOpen(true));
  }

  return (
    <>
      <SlidePanel open={panelOpen} onClose={() => setPanelOpen(false)} title="历史合同" anchor="sidebar">
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            requestDelete();
          }}
        >
          删除
        </button>
      </SlidePanel>
      <ConfirmDialog
        open={deleteOpen}
        title="删除合同"
        description="确认删除？"
        onConfirm={onConfirm ?? vi.fn()}
        onCancel={() => {
          onCancel?.();
          setDeleteOpen(false);
        }}
      />
    </>
  );
}

function setupOverlayRoot() {
  const root = document.createElement('div');
  root.id = MAIN_OVERLAY_ROOT_ID;
  document.body.appendChild(root);
  return root;
}

afterEach(() => {
  cleanup();
  document.body.style.overflow = '';
  document.getElementById(MAIN_OVERLAY_ROOT_ID)?.remove();
});

describe('SlidePanel + ConfirmDialog delete flow', () => {
  beforeEach(() => {
    setupOverlayRoot();
    vi.spyOn(performance, 'now').mockReturnValue(1000);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('keeps confirm dialog open after full delete click sequence', async () => {
    const onCancel = vi.fn();
    render(<HistoryDeleteHarness onCancel={onCancel} />);
    const deleteBtn = screen.getByRole('button', { name: '删除' });

    fireEvent.mouseDown(deleteBtn);
    fireEvent.mouseUp(deleteBtn);
    fireEvent.click(deleteBtn);

    await waitFor(() => {
      expect(screen.getByRole('alertdialog')).toBeTruthy();
    });

    expect(onCancel).not.toHaveBeenCalled();
  });

  it('allows backdrop dismiss after grace period', async () => {
    vi.restoreAllMocks();
    const onCancel = vi.fn();
    render(<HistoryDeleteHarness onCancel={onCancel} />);

    fireEvent.click(screen.getByRole('button', { name: '删除' }));

    await waitFor(() => {
      expect(screen.getByRole('alertdialog')).toBeTruthy();
    });

    const overlay = document.querySelector('.rl-dialog-overlay') as HTMLElement;
    await new Promise((resolve) => setTimeout(resolve, DIALOG_DISMISS_GRACE_MS + 50));

    fireEvent.mouseDown(overlay);
    fireEvent.click(overlay);

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
