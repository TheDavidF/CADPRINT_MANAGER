import { ApiError, request, requestBlob } from '../../services/api';
import type {
  Product,
  ProductCategory,
  ProductDetail,
  ProductFilters,
  ProductImage,
  ProductInput,
  ProductPage,
  ProductVariant,
  VariantInput,
} from './productTypes';

const products = '/products';
const categories = '/product-categories';
const productPath = (id: number) => products + '/' + id;

// Match complete JSON strings so names and descriptions are never rewritten.
const priceTokens =
  /("(?:basePrice|ownPrice|effectivePrice)"\s*:\s*)(-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)|"(?:[^"\\]|\\.)*"/g;

function decimalText(value: string) {
  const [mantissa, exponent] = value.toLowerCase().split('e');
  if (exponent === undefined) return mantissa;
  const [integer, fractional = ''] = mantissa.split('.');
  const digits = integer + fractional;
  const position = integer.length + Number(exponent);
  if (position <= 0) return '0.' + '0'.repeat(-position) + digits;
  if (position >= digits.length) return digits.padEnd(position, '0');
  return digits.slice(0, position) + '.' + digits.slice(position);
}

function catalogRequest<T>(token: string, path: string, options?: RequestInit) {
  return request<T>(
    token,
    path,
    options,
    (text) =>
      JSON.parse(
        text.replace(
          priceTokens,
          (token, key: string | undefined, value: string) =>
            key === undefined
              ? token
              : key + JSON.stringify(decimalText(value)),
        ),
      ) as T,
  );
}

function write<T>(
  token: string,
  path: string,
  method: 'POST' | 'PUT',
  payload: unknown,
) {
  return catalogRequest<T>(token, path, {
    method,
    body: JSON.stringify(payload),
  });
}

export function listProducts(
  token: string,
  filters: ProductFilters,
  page: number,
  signal: AbortSignal,
) {
  const query = new URLSearchParams({ page: String(page) });
  for (const [field, value] of Object.entries(filters)) {
    if (value.trim()) query.set(field, value.trim());
  }
  return catalogRequest<ProductPage>(token, products + '?' + query, { signal });
}

export const getProduct = (token: string, id: number) =>
  catalogRequest<ProductDetail>(token, productPath(id));

export function saveProduct(token: string, input: ProductInput, id?: number) {
  return write<Product>(
    token,
    id === undefined ? products : productPath(id),
    id === undefined ? 'POST' : 'PUT',
    input,
  );
}

export function listCategories(token: string, signal?: AbortSignal) {
  return request<ProductCategory[]>(token, categories, { signal });
}

export function saveCategory(
  token: string,
  name: string,
  current?: ProductCategory,
) {
  return write<ProductCategory>(
    token,
    current ? categories + '/' + current.id : categories,
    current ? 'PUT' : 'POST',
    { name, version: current?.version },
  );
}

export function saveVariant(
  token: string,
  productId: number,
  input: VariantInput,
  id?: number,
) {
  const path = productPath(productId) + '/variants';
  return write<ProductVariant>(
    token,
    id === undefined ? path : path + '/' + id,
    id === undefined ? 'POST' : 'PUT',
    input,
  );
}

export function setCategoryStatus(token: string, category: ProductCategory) {
  return write<ProductCategory>(
    token,
    categories + '/' + category.id + '/status',
    'PUT',
    { active: !category.active, version: category.version },
  );
}

export function setProductStatus(token: string, product: Product) {
  return write<Product>(token, productPath(product.id) + '/status', 'PUT', {
    active: !product.active,
    version: product.version,
  });
}

export function setVariantStatus(token: string, variant: ProductVariant) {
  return write<ProductVariant>(
    token,
    productPath(variant.productId) + '/variants/' + variant.id + '/status',
    'PUT',
    { active: !variant.active, version: variant.version },
  );
}

type ImageUpdate = { image: ProductImage | null; version: number };

export function getProductImage(
  token: string,
  id: number,
  signal: AbortSignal,
) {
  return requestBlob(token, productPath(id) + '/image', { signal });
}

export function uploadProductImage(
  token: string,
  product: Product,
  file: File,
) {
  const body = new FormData();
  body.set('file', file);
  body.set('version', String(product.version));
  return request<ImageUpdate>(token, productPath(product.id) + '/image', {
    method: 'PUT',
    body,
  });
}

export function removeProductImage(token: string, product: Product) {
  return request<ImageUpdate>(
    token,
    productPath(product.id) + '/image?version=' + product.version,
    { method: 'DELETE' },
  );
}

export function versionConflict(error: unknown) {
  return (
    error instanceof ApiError &&
    error.status === 409 &&
    /versión.*desactualizada/i.test(error.message)
  );
}

export function catalogError(error: unknown) {
  if (versionConflict(error))
    return 'El registro cambió desde que lo abriste. Recarga los datos antes de continuar.';
  if (error instanceof ApiError) {
    if (error.status === 401)
      return 'La sesión no está autorizada. Vuelve a iniciar sesión.';
    if (error.status === 403)
      return 'Tu usuario no tiene permiso para esta operación.';
    if (error.status === 413) return 'La imagen supera el límite de 5 MiB.';
    if (error.status === 415) return 'Selecciona una imagen JPEG o PNG.';
    if (
      error.status === 409 &&
      error.message === 'Conflicto de datos. Verifica SKU o referencias.'
    ) {
      return 'El SKU o nombre ya está en uso, o hay una referencia no válida.';
    }
    return error.message;
  }
  return 'No fue posible comunicarse con el servidor.';
}

const money = new Intl.NumberFormat('es-GT', {
  style: 'currency',
  currency: 'GTQ',
});
export function priceLabel(price: string | null) {
  if (price === null) return 'Sin precio';
  const [integer, fraction = ''] = price.split('.');
  return money
    .formatToParts(BigInt(integer))
    .map((part) =>
      part.type === 'fraction' ? fraction.padEnd(2, '0') : part.value,
    )
    .join('');
}
export const validPrice = (value: string) =>
  /^\d{1,14}(?:\.\d{1,2})?$/.test(value) && /[1-9]/.test(value);
