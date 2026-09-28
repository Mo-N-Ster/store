import { FileDown, FileUp, ImagePlus, Pencil, Plus, Trash2, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ConfirmDialog, EmptyState, IconButton, Modal, NumberInput, Select, TextInput } from '../../../design-system';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
import { selectFile } from '../../../services/api';
import { productService } from '../../../services/productService';
import { reportService } from '../../../services/reportService';
import type { Product } from '../../../types';
import { formatMoney } from '../../../utils/formatters';
import { productStockStatus } from '../../Cashier/posModel';
import { ProductDetailsDialog } from './ProductDetailsDialog';
import { can, type EffectivePermission } from '../../../security/permissions';
import { ArticleImage } from '../../../components/common/ArticleImage';

export function ProductList({ notify, userId, permissions, editorOnly = false, onCreated, onCancel }: { notify: (value: string) => void; userId: number; permissions: EffectivePermission[]; editorOnly?: boolean; onCreated?: (id: number) => void; onCancel?: () => void }) {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [rows, setRows] = useState<Product[]>([]);
  const [edit, setEdit] = useState<Partial<Product> | null>(editorOnly ? { stockQuantity: 0, minStockThreshold: 0 } : null);
  const [detail, setDetail] = useState<Product | null>(null);
  const [removeTarget, setRemoveTarget] = useState<Product | null>(null);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [stockFilter, setStockFilter] = useState<'all' | 'low' | 'out' | 'available'>('all');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [imageDraft, setImageDraft] = useState<{ token: string; previewDataUrl: string } | null>(null);
  const [removeImage, setRemoveImage] = useState(false);
  const saveLock = useRef(false);
  const load = () => productService.list({}).then(setRows);
  useEffect(() => { void load(); }, []);
  const filtered = useMemo(() => rows.filter((product) => {
    const status = productStockStatus(product);
    return (!category || product.category === category) && (stockFilter === 'all' || status === stockFilter) && [product.name, product.category, product.hashtag].filter(Boolean).join(' ').toLowerCase().includes(search.trim().toLowerCase());
  }).sort((a, b) => a.name.localeCompare(b.name)), [category, rows, search, stockFilter]);

  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (saveLock.current) return;
    saveLock.current = true; setSaving(true); setError('');
    const form = Object.fromEntries(new FormData(event.currentTarget));
    try {
      const savedId = await productService.save({ ...edit, ...form, price: +form.price, stockQuantity: editorOnly ? 0 : +form.stockQuantity, minStockThreshold: +form.minStockThreshold, imageToken: imageDraft?.token, removeImage });
      setEdit(null); await load(); notify(t('productSaved'));
      onCreated?.(Number(savedId));
    } catch (caught: any) {
      setError(caught.message?.includes('DUPLICATE_PRODUCT') ? t('productAlreadyExists') : t('operationFailed'));
    } finally { saveLock.current = false; setSaving(false); }
  };
  const closeEditor = () => { setEdit(null); setImageDraft(null); setRemoveImage(false); setError(''); onCancel?.(); };
  const openEditor = (product: Partial<Product>) => { setEdit(product); setImageDraft(null); setRemoveImage(false); setError(''); };
  const chooseImage = async () => {
    try {
      const selected = await productService.selectImage();
      if (selected) { setImageDraft(selected); setRemoveImage(false); }
    } catch (caught: any) {
      setError(caught.message?.includes('MEDIA_TOO_LARGE') ? t('articleImageTooLarge') : t('articleImageInvalid'));
    }
  };
  const exportPdf = async () => {
    document.body.classList.add('document-print-mode');
    try { await new Promise<void>((resolve) => requestAnimationFrame(() => resolve())); await reportService.exportPdf('STORE-stocks.pdf', { kind: 'stocks', version: 1, exportedAt: new Date().toISOString(), products: rows.map(({ id: _id, ...product }) => product) }); notify(t('stockPdfExported')); }
    finally { document.body.classList.remove('document-print-mode'); }
  };
  const importPdf = async () => {
    const file = await selectFile([{ name: 'PDF', extensions: ['pdf'] }]);
    if (!file) return;
    try { const count = await productService.importPdf(file); await load(); notify(t('stockPdfImported', { count })); }
    catch { setError(t('invalidStockPdf')); }
  };

  return <section className={editorOnly ? 'article-editor-only' : 'ops-page'} aria-labelledby={editorOnly ? undefined : 'products-title'}>
    <header className="ops-page__header"><div><span className="eyebrow">{t('dailyOperations')}</span><h1 id="products-title">{t('products')}</h1><p>{t('productsPageHint')}</p></div>{can(permissions, 'PRODUCTS', 'UPDATE') && <Button icon={Plus} size="lg" onClick={() => openEditor({})}>{t('newProduct')}</Button>}</header>
    <div className="ops-secondary-actions">{can(permissions, 'FINANCES', 'READ') && <Button variant="secondary" icon={FileDown} onClick={() => void exportPdf()}>{t('exportPdf')}</Button>}{can(permissions, 'PRODUCTS', 'CREATE') && <Button variant="secondary" icon={FileUp} onClick={() => void importPdf()}>{t('importPdf')}</Button>}</div>
    <div className="ops-filters"><TextInput type="search" label={t('searchProducts')} value={search} clearLabel={t('clearSearch')} onClear={search ? () => setSearch('') : undefined} onChange={(event) => setSearch(event.target.value)} /><Select label={t('category')} value={category} onChange={(event) => setCategory(event.target.value)}><option value="">{t('allCategories')}</option>{[...new Set(rows.map((product) => product.category))].sort().map((value) => <option key={value}>{value}</option>)}</Select><Select label={t('stockStatus')} value={stockFilter} onChange={(event) => setStockFilter(event.target.value as typeof stockFilter)}><option value="all">{t('allStockLevels')}</option><option value="available">{t('stockAvailable')}</option><option value="low">{t('stockLow')}</option><option value="out">{t('stockOut')}</option></Select></div>
    <div className="ops-result-count">{t('resultCount', { count: filtered.length })}</div>
    {!filtered.length ? <EmptyState title={t('noProductsFound')} description={t('adjustFiltersHint')} /> : <div className="ops-list">{filtered.map((product) => { const status = productStockStatus(product); return <article className="ops-list-item" key={product.id} tabIndex={0} onClick={() => setDetail(product)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setDetail(product); } }}><ArticleImage imageRef={product.imageRef} alt={t('articleImageAlt', { article: product.name })} className="article-image--thumbnail" /><div className="ops-list-item__main"><strong>{product.name}</strong><span>{product.category} · {product.hashtag || t('noIdentifier')}</span></div><strong className="ops-list-item__price">{formatMoney(product.price, currency)}</strong><span className={`ops-status ops-status--${status}`}>{t(status === 'out' ? 'stockOut' : status === 'low' ? 'stockLow' : 'stockAvailable')} · {product.stockQuantity}</span><div className="ops-list-item__actions">{can(permissions, 'PRODUCTS', 'UPDATE') && <IconButton icon={Pencil} label={t('editProduct', { product: product.name })} onClick={(event) => { event.stopPropagation(); openEditor(product); }} />}{can(permissions, 'PRODUCTS', 'DELETE') && <IconButton icon={Trash2} label={t('deleteProduct', { product: product.name })} onClick={(event) => { event.stopPropagation(); setRemoveTarget(product); }} />}</div></article>; })}</div>}
    {edit && <Modal title={edit.id ? t('editProductTitle') : t('newProduct')} description={t(editorOnly ? 'draftNoStockEffect' : 'productFormHint')} closeLabel={t('close')} onClose={() => !saving && closeEditor()} dismissible={!saving}><form className="ops-form" onSubmit={save}><fieldset><legend>{t('identity')}</legend><div className="article-image-field"><div>{imageDraft ? <img className="article-image article-image--preview" src={imageDraft.previewDataUrl} alt={t('articleImagePreview')} /> : removeImage ? <ArticleImage alt="" /> : <ArticleImage imageRef={edit.imageRef} alt={t('articleImageAlt', { article: edit.name || t('product') })} className="article-image--preview" />}</div><div className="article-image-field__actions"><Button type="button" variant="secondary" icon={ImagePlus} onClick={() => void chooseImage()}>{edit.imageRef || imageDraft ? t('replaceArticleImage') : t('chooseArticleImage')}</Button>{(edit.imageRef || imageDraft) && !removeImage && <Button type="button" variant="ghost" icon={X} onClick={() => { setImageDraft(null); setRemoveImage(true); }}>{t('removeArticleImage')}</Button>}<small>{t('articleImageHelp')}</small></div></div><TextInput name="name" label={t('name')} defaultValue={edit.name ?? ''} required /><TextInput name="hashtag" label={t('hashtag')} defaultValue={edit.hashtag ?? ''} /><TextInput name="category" label={t('category')} list="product-categories" defaultValue={edit.category ?? ''} required /><TextInput name="description" label={t('description')} defaultValue={edit.description ?? ''} /></fieldset><fieldset><legend>{t('pricingAndStock')}</legend><NumberInput name="price" label={`${t('price')} (${currency})`} min="0" step="0.01" defaultValue={edit.price ?? ''} required /><NumberInput name="stockQuantity" readOnly={editorOnly} label={t('stock')} min="0" step="1" defaultValue={edit.stockQuantity ?? ''} required /><NumberInput name="minStockThreshold" label={t('minimumThreshold')} min="0" step="1" defaultValue={edit.minStockThreshold ?? ''} required /></fieldset><datalist id="product-categories">{[...new Set(rows.map((product) => product.category))].sort().map((value) => <option key={value} value={value} />)}</datalist>{error && <p className="ds-field__error" role="alert">{error}</p>}<div className="ops-form__actions"><Button type="button" variant="secondary" disabled={saving} onClick={closeEditor}>{t('cancel')}</Button><Button type="submit" loading={saving} loadingLabel={t('saving')}>{t('save')}</Button></div></form></Modal>}
    {detail && <ProductDetailsDialog product={detail} currency={currency} onClose={() => setDetail(null)} />}
    {removeTarget && <ConfirmDialog title={t('deleteProductTitle')} description={t('confirmProductDeletion')} confirmLabel={t('delete')} cancelLabel={t('cancel')} danger onClose={() => setRemoveTarget(null)} onConfirm={() => { const target = removeTarget; setRemoveTarget(null); void productService.remove({ id: target.id, userId }).then(load).catch(() => setError(t('operationFailed'))); }} />}
  </section>;
}
