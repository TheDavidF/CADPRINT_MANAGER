export type Category = { id: number; name: string };

export type Material = {
  id: number;
  sku: string;
  name: string;
  category: string;
  categoryId: number;
  unit: string;
  stock: number;
  minStock: number;
  unitCost: number;
  active: boolean;
};
export type Movement = {
  id: number;
  type: string;
  quantity: number;
  beforeStock: number;
  afterStock: number;
  reason: string;
  username: string;
  createdAt: string;
  material: Material;
};
export type Supplier = {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  notes: string | null;
};
export type Dashboard = {
  materials: number;
  lowStock: number;
  inventoryValue: number;
  movements: number;
};

export type MaterialChange = {
  id: number;
  field: string;
  previousValue: string | null;
  newValue: string | null;
  username: string;
  changedAt: string;
};
