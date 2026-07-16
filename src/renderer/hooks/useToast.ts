import { useCallback, useEffect, useRef, useState } from 'react';
export function useToast() {
  const [toast, setToast] = useState('');
  const timer = useRef<number | undefined>(undefined);
  const notify = useCallback((message: string) => {
    window.clearTimeout(timer.current);
    setToast(message);
    timer.current = window.setTimeout(() => setToast(''), 3000);
  }, []);
  useEffect(() => () => window.clearTimeout(timer.current), []);
  return { toast, notify };
}
