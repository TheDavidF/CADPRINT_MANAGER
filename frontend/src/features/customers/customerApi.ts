import { request } from '../../services/api';

export const customerFields = [
  ['name', 'Nombre o razón social', 255],
  ['contact', 'Contacto', 255],
  ['email', 'Correo', 254],
  ['phone', 'Teléfono', 64],
  ['address', 'Dirección', 500],
] as const;

type CustomerInput = Record<(typeof customerFields)[number][0], string | null>;
export type Customer = CustomerInput & { id: number; name: string };

export const customerApi = {
  list: (token: string, signal?: AbortSignal) =>
    request<Customer[]>(token, '/customers', { signal }),
  save: (token: string, customer: CustomerInput, id?: number) =>
    request<Customer>(token, id ? '/customers/' + id : '/customers', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(customer),
    }),
};
