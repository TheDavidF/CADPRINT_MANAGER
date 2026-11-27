import { useEffect, useRef, useState } from 'react';
import { CatalogConfirm } from './CatalogConfirm';
import { ProductCategories } from './ProductCategories';
import { ProductDetails } from './ProductDetails';
import { ProductForm } from './ProductForm';
import { emptyFilters, ProductList } from './ProductList';
import {
  catalogError,
  getProduct,
  listCategories,
  listProducts,
  removeProductImage,
  saveCategory,
  saveProduct,
  saveVariant,
  setCategoryStatus,
  setProductStatus,
  setVariantStatus,
  uploadProductImage,
  versionConflict,
} from './productApi';
import type {
  Product,
  ProductCategory,
  ProductDetail,
  ProductFilters,
  ProductInput,
  ProductPage,
  VariantInput,
} from './productTypes';

type Confirmation = { label: string; run: () => Promise<boolean> };
type Feedback = {
  message: string;
  reload?: () => void;
  discardsForm?: boolean;
};

export function ProductsPage({
  token,
  roles,
}: {
  token: string;
  roles: string[];
}) {
  const admin = roles.includes('ROLE_ADMIN');
  const [tab, setTab] = useState<'products' | 'categories'>('products');
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [categoryLoading, setCategoryLoading] = useState(true);
  const [categoryError, setCategoryError] = useState('');
  const [categoryRequest, setCategoryRequest] = useState(0);
  const [filters, setFilters] = useState<ProductFilters>(emptyFilters);
  const [page, setPage] = useState(0);
  const [listRevision, setListRevision] = useState(0);
  const [result, setResult] = useState<ProductPage | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [detail, setDetail] = useState<ProductDetail | null>(null);
  const [editor, setEditor] = useState<'new' | 'edit' | null>(null);
  const [formRevision, setFormRevision] = useState(0);
  const [variantRevision, setVariantRevision] = useState(0);
  const [categoryRevision, setCategoryRevision] = useState(0);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const [error, setError] = useState<Feedback | null>(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    void listCategories(token, controller.signal)
      .then((rows) => {
        if (!controller.signal.aborted) setCategories(rows);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setCategoryError(catalogError(reason));
      })
      .finally(() => {
        if (!controller.signal.aborted) setCategoryLoading(false);
      });
    return () => controller.abort();
  }, [token, categoryRequest]);

  useEffect(() => {
    const controller = new AbortController();
    void listProducts(token, filters, page, controller.signal)
      .then((rows) => {
        if (controller.signal.aborted) return;
        if (page > 0 && page >= rows.totalPages) {
          setPage(Math.max(0, rows.totalPages - 1));
          return;
        }
        setResult(rows);
        setListLoading(false);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setListError(catalogError(reason));
          setResult(null);
          setListLoading(false);
        }
      });
    return () => controller.abort();
  }, [token, filters, page, listRevision]);

  function clearFeedback() {
    setError(null);
    setNotice('');
  }

  function refreshList() {
    setListError('');
    setListLoading(true);
    setListRevision((current) => current + 1);
  }

  async function run(
    operation: () => Promise<void>,
    message = '',
    reload?: () => void,
    discardsForm = false,
  ) {
    if (inFlight.current) return false;
    inFlight.current = true;
    setBusy(true);
    clearFeedback();
    try {
      await operation();
      setNotice(message);
      return true;
    } catch (reason) {
      const stale = versionConflict(reason);
      if (stale) setConfirmation(null);
      setError({
        message: catalogError(reason),
        reload: stale ? reload : undefined,
        discardsForm,
      });
      return false;
    } finally {
      setBusy(false);
      inFlight.current = false;
    }
  }

  function reloadProduct(id: number, reset?: 'product' | 'variant') {
    void run(async () => {
      setDetail(await getProduct(token, id));
      if (reset === 'product') setFormRevision((value) => value + 1);
      if (reset === 'variant') setVariantRevision((value) => value + 1);
      refreshList();
    });
  }

  function reloadCategories() {
    void run(async () => {
      setCategories(await listCategories(token));
      setCategoryRevision((value) => value + 1);
      refreshList();
    });
  }

  function open(id: number, edit = false) {
    void run(async () => {
      setDetail(await getProduct(token, id));
      setEditor(edit ? 'edit' : null);
    });
  }

  async function updateProductDetail(product: Product) {
    try {
      setDetail(await getProduct(token, product.id));
    } catch {
      setDetail((current) => (current ? { ...current, product } : current));
      setError({
        message:
          'El cambio se guardó. Recarga el detalle para actualizar las variantes y la imagen.',
        reload: () => reloadProduct(product.id),
      });
    }
  }

  function save(input: ProductInput) {
    const id = editor === 'edit' ? detail?.product.id : undefined;
    void run(
      async () => {
        const product = await saveProduct(token, input, id);
        if (id === undefined) setDetail({ product, variants: [], image: null });
        else await updateProductDetail(product);
        setEditor(null);
        refreshList();
      },
      'Producto guardado.',
      id === undefined ? undefined : () => reloadProduct(id, 'product'),
      true,
    );
  }

  function updateCategoryRow(category: ProductCategory) {
    setCategories((current) =>
      [...current.filter((row) => row.id !== category.id), category].sort(
        (left, right) => left.name.localeCompare(right.name, 'es-GT'),
      ),
    );
    refreshList();
  }

  function selectTab(next: 'products' | 'categories') {
    if (next === tab) return;
    clearFeedback();
    setTab(next);
    if (next === 'products' && detail && !editor) open(detail.product.id);
  }

  function saveVariantFor(
    detail: ProductDetail,
    input: VariantInput,
    id?: number,
  ) {
    return run(
      async () => {
        const variant = await saveVariant(token, detail.product.id, input, id);
        setDetail((current) =>
          current
            ? {
                ...current,
                variants: [
                  ...current.variants.filter((row) => row.id !== variant.id),
                  variant,
                ].sort((a, b) => a.id - b.id),
              }
            : current,
        );
      },
      'Variante guardada.',
      () => reloadProduct(detail.product.id, 'variant'),
      true,
    );
  }

  function confirm(label: string, action: () => Promise<boolean>) {
    clearFeedback();
    setConfirmation({ label, run: action });
  }

  const feedback = error && !confirmation && (
    <div className="notice">
      <span role="alert">{error.message}</span>
      {error.reload && (
        <button type="button" disabled={busy} onClick={error.reload}>
          {error.discardsForm
            ? 'Recargar y descartar cambios'
            : 'Recargar información'}
        </button>
      )}
    </div>
  );

  return (
    <>
      <div className="purchaseActions" aria-label="Vistas del catálogo">
        <button
          type="button"
          aria-pressed={tab === 'products'}
          disabled={busy}
          onClick={() => selectTab('products')}
        >
          Productos
        </button>
        <button
          type="button"
          aria-pressed={tab === 'categories'}
          disabled={busy}
          onClick={() => selectTab('categories')}
        >
          Categorías comerciales
        </button>
      </div>
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {feedback}
      {categoryLoading && <p role="status">Cargando categorías comerciales…</p>}
      {categoryError && (
        <div className="notice">
          <span role="alert">{categoryError}</span>
          <button
            onClick={() => {
              setCategoryError('');
              setCategoryLoading(true);
              setCategoryRequest((value) => value + 1);
            }}
          >
            Reintentar categorías
          </button>
        </div>
      )}
      {tab === 'categories' ? (
        !categoryLoading &&
        !categoryError && (
          <ProductCategories
            key={categoryRevision}
            categories={categories}
            admin={admin}
            busy={busy}
            onSave={(name, category) =>
              run(
                async () =>
                  updateCategoryRow(await saveCategory(token, name, category)),
                'Categoría guardada.',
                reloadCategories,
                true,
              )
            }
            onStatus={(category) =>
              confirm(
                (category.active ? 'Desactivar ' : 'Reactivar ') +
                  category.name +
                  '?',
                () =>
                  run(
                    async () =>
                      updateCategoryRow(
                        await setCategoryStatus(token, category),
                      ),
                    'Estado actualizado.',
                    reloadCategories,
                  ),
              )
            }
          />
        )
      ) : editor ? (
        <ProductForm
          key={formRevision}
          product={editor === 'edit' ? detail?.product : undefined}
          categories={categories}
          busy={busy}
          onSave={save}
          onCancel={() => {
            setEditor(null);
            clearFeedback();
          }}
        />
      ) : detail ? (
        <ProductDetails
          detail={detail}
          token={token}
          admin={admin}
          busy={busy}
          variantRevision={variantRevision}
          onBack={() => {
            setDetail(null);
            clearFeedback();
          }}
          onEdit={() => {
            clearFeedback();
            setEditor('edit');
          }}
          onStatus={() =>
            confirm(
              (detail.product.active ? 'Desactivar ' : 'Reactivar ') +
                detail.product.name +
                '?',
              () =>
                run(
                  async () => {
                    await updateProductDetail(
                      await setProductStatus(token, detail.product),
                    );
                    refreshList();
                  },
                  'Estado actualizado.',
                  () => reloadProduct(detail.product.id),
                ),
            )
          }
          onSaveVariant={(input, id) => saveVariantFor(detail, input, id)}
          onVariantStatus={(variant) =>
            confirm(
              (variant.active
                ? 'Desactivar variante '
                : 'Reactivar variante ') +
                variant.name +
                '?',
              () =>
                run(
                  async () => {
                    const next = await setVariantStatus(token, variant);
                    setDetail((current) =>
                      current
                        ? {
                            ...current,
                            variants: current.variants.map((row) =>
                              row.id === next.id ? next : row,
                            ),
                          }
                        : current,
                    );
                  },
                  'Estado actualizado.',
                  () => reloadProduct(detail.product.id),
                ),
            )
          }
          onUpload={(file) =>
            run(
              async () => {
                const next = await uploadProductImage(
                  token,
                  detail.product,
                  file,
                );
                setDetail({
                  ...detail,
                  product: { ...detail.product, version: next.version },
                  image: next.image,
                });
              },
              'Imagen guardada.',
              () => reloadProduct(detail.product.id),
            )
          }
          onRemove={() =>
            confirm('¿Retirar la imagen principal?', () =>
              run(
                async () => {
                  const next = await removeProductImage(token, detail.product);
                  setDetail({
                    ...detail,
                    product: { ...detail.product, version: next.version },
                    image: next.image,
                  });
                },
                'Imagen retirada.',
                () => reloadProduct(detail.product.id),
              ),
            )
          }
        />
      ) : (
        <ProductList
          categories={categories}
          applied={filters}
          result={result}
          loading={listLoading}
          error={listError}
          busy={busy}
          onApply={(next) => {
            setFilters(next);
            setPage(0);
            refreshList();
          }}
          onPage={(next) => {
            setPage(next);
            refreshList();
          }}
          onSelect={open}
          onNew={() => {
            clearFeedback();
            setEditor('new');
          }}
          onRetry={refreshList}
        />
      )}
      {confirmation && (
        <CatalogConfirm
          label={confirmation.label}
          busy={busy}
          error={error?.message ?? ''}
          onClose={() => {
            clearFeedback();
            setConfirmation(null);
          }}
          onConfirm={() =>
            void confirmation.run().then((saved) => {
              if (saved) setConfirmation(null);
            })
          }
        />
      )}
    </>
  );
}
