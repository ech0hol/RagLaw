import { useEffect, useRef, type MouseEvent } from 'react';
import { isDialogDismissAllowed } from './dialogDismiss';
import { useDialogOpenedAt } from './useDialogOpenedAt';

export function useDialogOverlayDismiss(
  open: boolean,
  onDismiss: () => void,
  options?: { blocked?: boolean },
) {
  const openedAtRef = useDialogOpenedAt(open);
  const pointerDownOnBackdropRef = useRef(false);

  useEffect(() => {
    if (!open) return;
    pointerDownOnBackdropRef.current = false;
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && !options?.blocked && isDialogDismissAllowed(openedAtRef.current)) {
        onDismiss();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, onDismiss, options?.blocked, openedAtRef]);

  function onBackdropMouseDown(e: MouseEvent<HTMLDivElement>) {
    pointerDownOnBackdropRef.current = e.target === e.currentTarget;
  }

  function onBackdropClick(e: MouseEvent<HTMLDivElement>) {
    if (
      options?.blocked
      || e.target !== e.currentTarget
      || !pointerDownOnBackdropRef.current
      || !isDialogDismissAllowed(openedAtRef.current)
    ) {
      return;
    }
    onDismiss();
  }

  return { onBackdropMouseDown, onBackdropClick };
}
