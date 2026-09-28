import { useEffect, useState } from 'react';
import { Package } from 'lucide-react';
import { productService } from '../../services/productService';

const cache = new Map<string, string | null>();

export function ArticleImage({ imageRef, alt, className = '' }: { imageRef?: string | null; alt: string; className?: string }) {
  const [source, setSource] = useState<string | null>(() => imageRef ? cache.get(imageRef) ?? null : null);
  useEffect(() => {
    let active = true;
    if (!imageRef) { setSource(null); return; }
    const cached = cache.get(imageRef);
    if (cached !== undefined) { setSource(cached); return; }
    void productService.image(imageRef).then((value) => {
      cache.set(imageRef, value);
      if (active) setSource(value);
    }).catch(() => { if (active) setSource(null); });
    return () => { active = false; };
  }, [imageRef]);
  return source
    ? <img className={`article-image ${className}`.trim()} src={source} alt={alt} onError={() => setSource(null)} />
    : <span className={`article-image article-image--placeholder ${className}`.trim()} aria-hidden="true"><Package /></span>;
}
