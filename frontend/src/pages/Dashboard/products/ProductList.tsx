import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Product } from '../../../types';
import { productService } from '../../../services/productService';
import { formatMoney } from '../../../utils/formatters';
import { csvEscape } from '../../../utils/domain';
import { selectFile } from '../../../services/api';
export function ProductList({ notify, userId }: { notify: (x: string) => void; userId: number }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<Product[]>([]);
  const [edit, setEdit] = useState<any>(null);
  const load = () => productService.list({}).then(setRows);
  useEffect(() => {
    void load();
  }, []);
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    await productService.save({
      ...edit,
      ...form,
      price: +form.price,
      stockQuantity: +form.stockQuantity,
      minStockThreshold: +form.minStockThreshold,
    });
    setEdit(null);
    await load();
    notify('Produit enregistré');
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
          <span className="eyebrow">Inventaire</span>
          <h1>{t('products')}</h1>
        </div>
        <div>
          <button className="ghost" onClick={exportCsv}>
            Exporter CSV
          </button>
          <button className="ghost" onClick={importCsv}>
            Importer CSV
          </button>
          <button onClick={() => setEdit({})}>+ {t('add')}</button>
        </div>
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
            {rows.map((product) => (
              <tr key={product.id}>
                <td>
                  <b>{product.name}</b>
                  <small>{product.hashtag}</small>
                </td>
                <td>
                  <span className="pill">{product.category}</span>
                </td>
                <td>{formatMoney(product.price)}</td>
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
                      confirm('Supprimer définitivement ce produit ?') &&
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
              ['name', 'Nom'],
              ['hashtag', 'Hashtag'],
              ['category', t('category')],
              ['description', 'Description'],
              ['price', t('price')],
              ['stockQuantity', t('stock')],
              ['minStockThreshold', 'Seuil minimum'],
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
    </>
  );
}
