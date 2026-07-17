import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Product } from '../../../types';
import { productService } from '../../../services/productService';
import { formatMoney } from '../../../utils/formatters';
import { csvEscape } from '../../../utils/domain';
import { selectFile } from '../../../services/api';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
import { CriticalDialog } from '../../../components/UI/CriticalDialog';
export function ProductList({ notify, userId }: { notify: (x: string) => void; userId: number }) {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [criticalMessage, setCriticalMessage] = useState('');
  const [rows, setRows] = useState<Product[]>([]);
  const [edit, setEdit] = useState<any>(null);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
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
  const exportCsv = () =>
    productService.exportCsv(
      [
        'name,hashtag,category,description,price,stockQuantity,minStockThreshold',
        ...rows.map((item) =>
          [
            item.name,
            item.hashtag,
            item.category,
            item.description,
            item.price,
            item.stockQuantity,
            item.minStockThreshold,
          ]
            .map(csvEscape)
            .join(','),
        ),
      ].join('\n'),
    );
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
          <h1>{t('products')}</h1>
        </div>
        <div>
          <button className="ghost" onClick={exportCsv}>
            {t('exportCsv')}
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
              .map((product) => (
                <tr key={product.id}>
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
                    <button onClick={() => setEdit(product)}>✎</button>
                    <button
                      className="danger"
                      onClick={() =>
                        confirm(t('confirmProductDeletion')) &&
                        productService.remove({ id: product.id, userId }).then(load)
                      }
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
    </>
  );
}
