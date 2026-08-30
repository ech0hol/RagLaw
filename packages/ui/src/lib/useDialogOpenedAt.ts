import { useRef } from 'react';

export function useDialogOpenedAt(open: boolean) {
  const openedAtRef = useRef(0);
  const prevOpenRef = useRef(false);

  if (open && !prevOpenRef.current) {
    openedAtRef.current = performance.now();
  }
  if (!open) {
    openedAtRef.current = 0;
  }
  prevOpenRef.current = open;

  return openedAtRef;
}
