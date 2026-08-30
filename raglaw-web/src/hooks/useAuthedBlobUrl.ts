import { useEffect, useState } from 'react';
import { fetchAuthedBlobUrl } from '../lib/api';

export function useAuthedBlobUrl(path: string | null, overrideMimeType?: string) {
  const [url, setUrl] = useState<string | null>(null);
  const [mimeType, setMimeType] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!path) {
      setUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
      setMimeType(null);
      setLoading(false);
      setError(null);
      return;
    }

    let cancelled = false;
    let objectUrl: string | null = null;
    setLoading(true);
    setError(null);

    void fetchAuthedBlobUrl(path, overrideMimeType)
      .then((result) => {
        if (cancelled) {
          if (result?.url) URL.revokeObjectURL(result.url);
          return;
        }
        objectUrl = result?.url ?? null;
        if (!result?.url) {
          setError('无法加载原件');
        }
        setUrl(result?.url ?? null);
        setMimeType(result?.mimeType ?? null);
        setLoading(false);
      })
      .catch(() => {
        if (!cancelled) {
          setError('无法加载原件');
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [path, overrideMimeType]);

  return { url, mimeType, loading, error };
}
