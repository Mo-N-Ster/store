import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Product } from '../../../types';
import { productService } from '../../../services/productService';
import { formatMoney } from '../../../utils/formatters';
import { selectFile } from '../../../services/api';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
import { CriticalDialog } from '../../../components/UI/CriticalDialog';
import { ProductDetailsDialog } from './ProductDetailsDialog';
import { reportService } from '../../../services/reportService';
export function ProductList({ notify, userId }: { notify: (x: string) => void; userId: number }) {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [criticalMessage, setCriticalMessage] = useState('');
  const [rows, setRows] = useState<Product[]>([]);
  const [edit, setEdit] = useState<any>(null);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [detail, setDetail] = useState<Product | null>(null);
  const load = () => productService.list({}).then(setRows);
  useEffect(() => {
    void load();
  }, []);
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    try {
      await productService.save({
        ...edit,
        ...form,
        price: +form.price,
        stockQuantity: +form.stockQuantity,
        minStockThreshold: +form.minStockThreshold,
      });
      setEdit(null);
      await load();
      notify(t('productSaved'));
    } catch (error: any) {
      setCriticalMessage(
        error.message?.includes('DUPLICATE_PRODUCT')
          ? t('productAlreadyExists')
          : t('operationFailed'),
      );
    }
  };
  const exportPdf = async () => {
    document.body.classList.add('document-print-mode');
    try {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      await reportService.exportPdf('STORE-stocks.pdf');
    } finally {
      document.body.classList.remove('document-print-mode');
    }
  };
  const importCsv = async () => {
    const file = await selectFile([{ name: 'CSV', extensions: ['csv'] }]);
    if (!file) return;
    const count = await productService.importCsv(file);
    await load();
    notify(`${count} produits importés ou mis à jour.`);
  };
  return (
    <>
      <div className="titlebar">
        <div>
          <span className="eyebrow">{t('inventory')}</span>
          <h1>{t('stocks')}</h1>
        </div>
        <div>
          <button className="ghost" onClick={exportPdf}>
            {t('exportPdf')}
          </button>
          <button className="ghost" onClick={importCsv}>
            {t('importCsv')}
          </button>
          <button onClick={() => setEdit({})}>+ {t('add')}</button>
        </div>
      </div>
      <div className="filters">
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('searchProducts')}
        />
        <select value={category} onChange={(event) => setCategory(event.target.value)}>
          <option value="">{t('allCategories')}</option>
          {[...new Set(rows.map((product) => product.category))].map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </div>
      <div className="table-shell">
        <table>
          <thead>
            <tr>
              <th>{t('products')}</th>
              <th>{t('category')}</th>
              <th>{t('price')}</th>
              <th>{t('stock')}</th>
              <th>{t('actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows
              .filter(
                (product) =>
                  (!category || product.category === category) &&
                  [product.name, product.category, product.hashtag]
                    .filter(Boolean)
                    .join(' ')
                    .toLowerCase()
                    .includes(search.trim().toLowerCase()),
              )
              .sort((a, b) => a.name.localeCompare(b.name))
              .map((product) => (
                <tr className="clickable-row" key={product.id} onClick={() => setDetail(product)}>
                  <td>
                    <b>{product.name}</b>
                    <small>{product.hashtag}</small>
                  </td>
                  <td>
                    <span className="pill">{product.category}</span>
                  </td>
                  <td>{formatMoney(product.price, currency)}</td>
                  <td>
                    <span
                      className={
                        product.stockQuantity <= product.minStockThreshold
                          ? 'stock-pill low-stock'
                          : 'stock-pill'
                      }
                    >
                      {product.stockQuantity}
                    </span>
                  </td>
                  <td>
                    <button
                      onClick={(event) => {
                        event.stopPropagation();
                        setEdit(product);
                      }}
                    >
                      ✎
                    </button>
                    <button
                      className="danger"
                      onClick={(event) => {
                        event.stopPropagation();
                        if (confirm(t('confirmProductDeletion')))
                          void productService.remove({ id: product.id, userId }).then(load);
                      }}
                    >
                      🗑
                    </button>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
      {edit && (
        <div className="modal">
          <form className="form-modal" onSubmit={save}>
            <h2>{t('products')}</h2>
            {[
              ['name', t('name')],
              ['hashtag', t('hashtag')],
              ['category', t('category')],
              ['description', t('description')],
              ['price', `${t('price')} (${currency})`],
              ['stockQuantity', t('stock')],
              ['minStockThreshold', t('minimumThreshold')],
            ].map(([name, label]) => (
              <label key={name}>
                {label}
                <input
                  name={name}
                  type={
                    ['price', 'stockQuantity', 'minStockThreshold'].includes(name)
                      ? 'number'
                      : 'text'
                  }
                  step={name === 'price' ? '0.01' : undefined}
                  min="0"
                  defaultValue={edit[name] ?? ''}
                  required={[
                    'name',
                    'category',
                    'price',
                    'stockQuantity',
                    'minStockThreshold',
                  ].includes(name)}
                />
              </label>
            ))}
            <button>{t('save')}</button>
            <button type="button" className="ghost" onClick={() => setEdit(null)}>
              {t('close')}
            </button>
          </form>
        </div>
      )}
      {criticalMessage && (
        <CriticalDialog message={criticalMessage} onClose={() => setCriticalMessage('')} />
      )}
      {detail && (
        <ProductDetailsDialog
          product={detail}
          currency={currency}
          onClose={() => setDetail(null)}
        />
      )}
    </>
  );
}
