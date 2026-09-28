import { AlertTriangle, Boxes, ClipboardCheck, History, SlidersHorizontal } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Button,
  ConfirmDialog,
  EmptyState,
  Modal,
  NumberInput,
  Select,
  Tabs,
  TextInput,
} from '../../../design-system';
import { operationsService } from '../../../services/operationsService';
import { productService } from '../../../services/productService';
import type { Product } from '../../../types';
import { productStockStatus } from '../../Cashier/posModel';
import { can, type EffectivePermission } from '../../../security/permissions';

type InventoryDraft = {
  id: number;
  reference: string;
  lines: Array<{ product: Product; counted: number; saved: boolean }>;
};

export function StockPage({
  userId,
  notify,
  permissions,
}: {
  userId: number;
  notify: (message: string) => void;
  permissions: EffectivePermission[];
}) {
  const { t } = useTranslation();
  const [tab, setTab] = useState('state');
  const [products, setProducts] = useState<Product[]>([]);
  const [movements, setMovements] = useState<any[]>([]);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [movementType, setMovementType] = useState('');
  const [category, setCategory] = useState('');
  const [productId, setProductId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [adjust, setAdjust] = useState<Product | null>(null);
  const [inventory, setInventory] = useState<InventoryDraft | null>(null);
  const [inventoryNote, setInventoryNote] = useState('');
  const [confirmInventory, setConfirmInventory] = useState(false);
  const [busy, setBusy] = useState(false);
  const validateLock = useRef(false);
  const loadProducts = () => productService.list({}).then(setProducts);
  const loadMovements = () => {
    if (from && to && from > to) { notify(t('invalidDateRange')); return Promise.resolve(); }
    return operationsService.stockMovements({ reason: movementType, productId: Number(productId) || 0, category, from, to, limit: 500 }).then(setMovements).catch(() => notify(t('operationFailed')));
  };
  useEffect(() => {
    void Promise.all([loadProducts(), loadMovements()]);
  }, []);
  useEffect(() => { void loadMovements(); }, [movementType, productId, category, from, to]);
  const filtered = useMemo(
    () =>
      products.filter((product) => {
        const status = productStockStatus(product);
        return (
          (statusFilter === 'all' || status === statusFilter) &&
          (!category || product.category === category) &&
          (!productId || product.id === Number(productId)) &&
          [product.name, product.category]
            .join(' ')
            .toLowerCase()
            .includes(search.trim().toLowerCase())
        );
      }),
    [products, search, statusFilter, category, productId],
  );
  const lowProducts = filtered.filter((product) => productStockStatus(product) !== 'available');
  const filteredMovements = movements.filter(
    (row) =>
      [row.product, row.category, row.reason, row.sourceReference]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(search.trim().toLowerCase()),
  );

  const statePanel = (
    <div className="ops-domain">
      <div className="ops-filters stock-filters">
        <TextInput
          type="search"
          label={t('searchProducts')}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Select
          label={t('stockStatus')}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value)}
        >
          <option value="all">{t('allStockLevels')}</option>
          <option value="available">{t('stockAvailable')}</option>
          <option value="low">{t('stockLow')}</option>
          <option value="out">{t('stockOut')}</option>
        </Select>
      </div>
      <div className="ops-list">
        {filtered.map((product) => {
          const status = productStockStatus(product);
          return (
            <article className="ops-list-item stock-state-row" key={product.id}>
              <div className="ops-list-item__main">
                <strong>{product.name}</strong>
                <span>{product.category}</span>
              </div>
              <strong>{product.stockQuantity}</strong>
              <span className={`ops-status ops-status--${status}`}>
                {t(
                  status === 'out' ? 'stockOut' : status === 'low' ? 'stockLow' : 'stockAvailable',
                )}
              </span>
              {can(permissions, 'STOCKS', 'UPDATE') && <Button
                size="sm"
                variant="secondary"
                icon={SlidersHorizontal}
                onClick={() => setAdjust(product)}
              >
                {t('adjustStock')}
              </Button>}
            </article>
          );
        })}
      </div>
    </div>
  );
  const movementPanel = (
    <div className="ops-domain">
      <div className="ops-filters stock-filters">
        <TextInput type="date" label={t('from')} value={from} max={to || undefined} onChange={(event) => setFrom(event.target.value)} />
        <TextInput type="date" label={t('to')} value={to} min={from || undefined} onChange={(event) => setTo(event.target.value)} />
        <TextInput
          type="search"
          label={t('searchMovements')}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Select
          label={t('movementType')}
          value={movementType}
          onChange={(event) => setMovementType(event.target.value)}
        >
          <option value="">{t('allMovementTypes')}</option>
          {movementTypes.map((value) => (
            <option key={value} value={value}>{t(movementKey(value))}</option>
          ))}
        </Select>
      </div>
      {!filteredMovements.length ? (
        <EmptyState title={t('noMovements')} />
      ) : (
        <div className="ops-list">
          {filteredMovements.map((row, index) => (
            <article
              className="ops-list-item stock-movement-row"
              key={row.id || `${row.createdAt}-${row.productId}-${row.reason}-${index}`}
            >
              <div className="ops-list-item__main">
                <strong>{row.product}</strong>
                <span>
                  {row.category}
                </span>
                {row.sourceReference && <small>{t('reference')}: {row.sourceReference}</small>}
              </div>
              <span>{new Date(row.createdAt).toLocaleString()}</span>
              <span>{t(movementKey(row.reason))}{row.adjustmentReason && <small> · {row.adjustmentReason}</small>}</span>
              <b aria-label={row.quantity > 0 ? t('entries') : t('exits')}>{row.quantity > 0 ? '+' : ''}{row.quantity}</b>
            </article>
          ))}
        </div>
      )}
    </div>
  );
  const alertsPanel = (
    <div className="ops-domain">
      {!lowProducts.length ? (
        <EmptyState title={t('noStockAlerts')} description={t('stockHealthyHint')} />
      ) : (
        <div className="ops-list">
          {lowProducts.map((product) => {
            const status = productStockStatus(product);
            return (
              <article className="ops-list-item stock-alert-row" key={product.id}>
                <div className="ops-list-item__main">
                  <strong>{product.name}</strong>
                  <span>
                    {t('stockAlertMessage', {
                      product: product.name,
                      stock: product.stockQuantity,
                      threshold: product.minStockThreshold,
                    })}
                  </span>
                </div>
                <span className={`ops-status ops-status--${status}`}>
                  {t(status === 'out' ? 'stockOut' : 'stockLow')}
                </span>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
  const inventoryPanel = (
    <div className="ops-domain">
      {!inventory ? (
        <div className="ops-start-card">
          <ClipboardCheck aria-hidden="true" />
          <h2>{t('newInventory')}</h2>
          <p>{t('inventoryStartHint')}</p>
          <TextInput
            label={t('note')}
            value={inventoryNote}
            onChange={(event) => setInventoryNote(event.target.value)}
          />
          {can(permissions, 'STOCKS', 'CREATE') && <Button
            disabled={busy || !products.length}
            loading={busy}
            loadingLabel={t('saving')}
            onClick={() => {
              if (busy) return;
              setBusy(true);
              void operationsService
                .startInventory({ userId, note: inventoryNote })
                .then((created: any) =>
                  setInventory({
                    ...created,
                    lines: products.map((product) => ({
                      product,
                      counted: product.stockQuantity,
                      saved: true,
                    })),
                  }),
                )
                .catch(() => notify(t('operationFailed')))
                .finally(() => setBusy(false));
            }}
          >
            {t('createInventoryDraft')}
          </Button>}
        </div>
      ) : (
        <>
          <Alert variant="warning" title={t('draft')}>
            {t('inventoryDraftWarning', { reference: inventory.reference })}
          </Alert>
          <div className="ops-inventory-list">
            {inventory.lines.map((line) => (
              <article key={line.product.id}>
                <div>
                  <strong>{line.product.name}</strong>
                  <span>
                    {t('expected')}: {line.product.stockQuantity}
                  </span>
                </div>
                <NumberInput
                  label={t('counted')}
                  min="0"
                  step="1"
                  value={line.counted}
                  onChange={(event) => {
                    const counted = Math.max(0, Math.floor(Number(event.target.value) || 0));
                    setInventory((current) =>
                      current
                        ? {
                            ...current,
                            lines: current.lines.map((item) =>
                              item.product.id === line.product.id
                                ? { ...item, counted, saved: false }
                                : item,
                            ),
                          }
                        : current,
                    );
                  }}
                  onBlur={() => {
                    if (line.saved) return;
                    void operationsService
                      .recordInventoryLine({
                        inventoryId: inventory.id,
                        productId: line.product.id,
                        countedQuantity: line.counted,
                      })
                      .then(() =>
                        setInventory((current) =>
                          current
                            ? {
                                ...current,
                                lines: current.lines.map((item) =>
                                  item.product.id === line.product.id
                                    ? { ...item, saved: true }
                                    : item,
                                ),
                              }
                            : current,
                        ),
                      )
                      .catch(() => notify(t('operationFailed')));
                  }}
                />
                <span
                  className={`ops-difference ${line.counted - line.product.stockQuantity === 0 ? '' : 'ops-difference--changed'}`}
                >
                  {t('difference')}: {line.counted - line.product.stockQuantity}
                </span>
              </article>
            ))}
          </div>
          {can(permissions, 'STOCKS', 'VALIDATE') && <Button
            size="lg"
            disabled={inventory.lines.some((line) => !line.saved)}
            onClick={() => setConfirmInventory(true)}
          >
            {t('reviewAndValidate')}
          </Button>}
        </>
      )}
    </div>
  );

  return (
    <section className="ops-page" aria-labelledby="stock-title">
      <header className="ops-page__header">
        <div>
          <span className="eyebrow">{t('dailyOperations')}</span>
          <h1 id="stock-title">{t('stock')}</h1>
          <p>{t('stockPageHint')}</p>
        </div>
      </header>
      {tab !== 'inventory' && <div className="ops-filters stock-filters">
        <Select label={t('product')} value={productId} onChange={(event) => setProductId(event.target.value)}>
          <option value="">{t('allProducts')}</option>
          {products.map((product) => <option key={product.id} value={product.id}>{product.name}</option>)}
        </Select>
        <Select label={t('category')} value={category} onChange={(event) => setCategory(event.target.value)}>
          <option value="">{t('allCategories')}</option>
          {[...new Set(products.map((product) => product.category))].sort().map((value) => <option key={value}>{value}</option>)}
        </Select>
        <Button variant="ghost" onClick={() => { setCategory(''); setProductId(''); setFrom(''); setTo(''); setSearch(''); setMovementType(''); setStatusFilter('all'); }}>{t('resetFilters')}</Button>
      </div>}
      <Tabs
        ariaLabel={t('stockSections')}
        active={tab}
        onChange={setTab}
        tabs={[
          {
            id: 'state',
            label: (
              <>
                <Boxes size={16} /> {t('stockState')}
              </>
            ),
            panel: statePanel,
          },
          {
            id: 'movements',
            label: (
              <>
                <History size={16} /> {t('movements')}
              </>
            ),
            panel: movementPanel,
          },
          {
            id: 'inventory',
            label: (
              <>
                <ClipboardCheck size={16} /> {t('inventories')}
              </>
            ),
            panel: inventoryPanel,
          },
          {
            id: 'alerts',
            label: (
              <>
                <AlertTriangle size={16} /> {t('alerts')}
              </>
            ),
            panel: alertsPanel,
          },
        ]}
      />
      {adjust && (
        <Modal
          title={t('adjustStock')}
          description={adjust.name}
          closeLabel={t('close')}
          onClose={() => !busy && setAdjust(null)}
          dismissible={!busy}
        >
          <form
            className="ops-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (busy) return;
              const form = new FormData(event.currentTarget);
              setBusy(true);
              void operationsService
                .adjustStock({
                  productId: adjust.id,
                  newQuantity: Number(form.get('newQuantity')),
                  reason: String(form.get('reason')),
                  userId,
                })
                .then(async () => {
                  setAdjust(null);
                  await Promise.all([loadProducts(), loadMovements()]);
                  notify(t('stockAdjusted'));
                })
                .catch(() => notify(t('operationFailed')))
                .finally(() => setBusy(false));
            }}
          >
            <Alert variant="info" title={t('currentStock')}>
              {adjust.stockQuantity}
            </Alert>
            <NumberInput
              name="newQuantity"
              label={t('resultingStock')}
              min="0"
              step="1"
              defaultValue={adjust.stockQuantity}
              required
            />
            <TextInput name="reason" label={t('reason')} minLength={3} required />
            <div className="ops-form__actions">
              <Button type="button" variant="secondary" onClick={() => setAdjust(null)}>
                {t('cancel')}
              </Button>
              <Button type="submit" loading={busy} loadingLabel={t('saving')}>
                {t('confirmAdjustment')}
              </Button>
            </div>
          </form>
        </Modal>
      )}
      {confirmInventory && inventory && (
        <ConfirmDialog
          title={t('validateInventory')}
          description={t('validateInventoryConsequence')}
          confirmLabel={t('validate')}
          cancelLabel={t('cancel')}
          onClose={() => setConfirmInventory(false)}
          onConfirm={() => {
            if (validateLock.current) return;
            validateLock.current = true;
            setBusy(true);
            void operationsService
              .validateInventory({ inventoryId: inventory.id, userId })
              .then(async () => {
                setConfirmInventory(false);
                setInventory(null);
                setInventoryNote('');
                await Promise.all([loadProducts(), loadMovements()]);
                notify(t('inventoryValidated'));
              })
              .catch(() => notify(t('operationFailed')))
              .finally(() => {
                validateLock.current = false;
                setBusy(false);
              });
          }}
        />
      )}
    </section>
  );
}
import { movementKey, movementTypes } from '../../../utils/entityLabels';
