export const productionModes = {
  BAJO_PEDIDO: 'Bajo pedido',
  PARA_EXISTENCIAS: 'Para existencias',
} as const;
export const pricingModes = {
  PRECIO_BASE: 'Precio base',
  REQUIERE_COTIZACION: 'Requiere cotización',
} as const;

export type ProductionMode = keyof typeof productionModes;
export type PricingMode = keyof typeof pricingModes;

export type ProductCategory = {
  id: number;
  name: string;
  active: boolean;
  version: number;
};

export type Product = {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  category: ProductCategory;
  productionMode: ProductionMode;
  pricingMode: PricingMode;
  basePrice: string | null;
  effectivePrice: string | null;
  requiresQuotation: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ProductVariant = {
  id: number;
  productId: number;
  sku: string;
  name: string;
  size: string | null;
  color: string | null;
  presentation: string | null;
  pricingMode: PricingMode;
  basePrice: string | null;
  ownPrice: string | null;
  effectivePrice: string | null;
  requiresQuotation: boolean;
  active: boolean;
  commerciallyActive: boolean;
  version: number;
};

export type ProductImage = {
  contentType: string;
  fileSize: number;
  createdAt: string;
};

export type ProductDetail = {
  product: Product;
  variants: ProductVariant[];
  image: ProductImage | null;
};

export type ProductPage = {
  content: Product[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ProductFilters = {
  search: string;
  categoryId: string;
  productionMode: string;
  pricingMode: string;
  active: string;
};

export type ProductInput = {
  sku: string;
  name: string;
  description: string | null;
  categoryId: number;
  productionMode: ProductionMode;
  pricingMode: PricingMode;
  basePrice: string | null;
  version?: number;
};

export type VariantInput = {
  sku: string;
  name: string;
  size: string | null;
  color: string | null;
  presentation: string | null;
  ownPrice: string | null;
  version?: number;
};
