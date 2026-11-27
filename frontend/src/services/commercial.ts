import { request } from './api';

export function exactRequest<T>(
  token: string,
  path: string,
  fields: string[],
  options?: RequestInit,
) {
  const tokens = new RegExp(
    '("(?:' +
      fields.join('|') +
      ')"\\s*:\\s*)(-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|"(?:[^"\\\\]|\\\\.)*"',
    'g',
  );
  return request<T>(
    token,
    path,
    options,
    (json) =>
      JSON.parse(
        json.replace(tokens, (matched, field?: string, decimal?: string) =>
          field && decimal
            ? field + JSON.stringify(expandDecimal(decimal))
            : matched,
        ),
      ) as T,
  );
}

function expandDecimal(value: string) {
  const [mantissa, exponent] = value.toLowerCase().split('e');
  if (exponent === undefined) return value;
  const sign = mantissa.startsWith('-') ? '-' : '';
  const [integer, fraction = ''] = mantissa.slice(sign.length).split('.');
  const digits = integer + fraction;
  const point = integer.length + Number(exponent);
  if (point <= 0) return sign + '0.' + '0'.repeat(-point) + digits;
  if (point >= digits.length) return sign + digits.padEnd(point, '0');
  return sign + digits.slice(0, point) + '.' + digits.slice(point);
}

const grouping = new Intl.NumberFormat('es-GT');
export function gtq(value: string, minimum = 2) {
  const [integer, fraction = ''] = value.split('.');
  const digits = grouping.format(BigInt(integer));
  const sign = integer.startsWith('-') && BigInt(integer) === 0n ? '-' : '';
  return 'Q ' + sign + digits + '.' + fraction.padEnd(minimum, '0');
}

export const body = (data: unknown, method = 'POST'): RequestInit => ({
  method,
  body: JSON.stringify(data),
});

export const message = (error: unknown) =>
  error instanceof Error
    ? error.message
    : 'No fue posible completar la operación.';
