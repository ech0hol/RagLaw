import { useCallback, useEffect, useRef, type RefObject } from 'react';

const THRESHOLD = 64;

export function useStickyScroll(
  listRef: RefObject<HTMLDivElement | null>,
  scrollDeps: unknown[],
) {
  const pinnedRef = useRef(true);

  const onScroll = useCallback(() => {
    const el = listRef.current;
    if (!el) return;
    pinnedRef.current =
      el.scrollHeight - el.scrollTop - el.clientHeight < THRESHOLD;
  }, [listRef]);

  const pinToBottom = useCallback(() => {
    pinnedRef.current = true;
  }, []);

  useEffect(() => {
    const list = listRef.current;
    if (!list || !pinnedRef.current) return;
    list.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
  }, scrollDeps);

  return { onScroll, pinToBottom };
}
