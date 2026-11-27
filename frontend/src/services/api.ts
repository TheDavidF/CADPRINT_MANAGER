export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const apiBase = (import.meta.env.VITE_API_BASE_URL || '/api').replace(
  /\/$/,
  '',
);

async function authenticatedResponse(
  token: string,
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set('Authorization', 'Basic ' + token);
  if (typeof options.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(apiBase + path, { ...options, headers });
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    const detail =
      body && typeof body === 'object' && 'error' in body ? body.error : null;
    throw new ApiError(
      response.status,
      typeof detail === 'string' ? detail : 'Error HTTP ' + response.status,
    );
  }

  return response;
}

export async function request<T>(
  token: string,
  path: string,
  options: RequestInit = {},
  decode?: (json: string) => T,
): Promise<T> {
  const response = await authenticatedResponse(token, path, options);
  return decode
    ? decode(await response.text())
    : (response.json() as Promise<T>);
}

export async function requestBlob(
  token: string,
  path: string,
  options: RequestInit = {},
): Promise<Blob> {
  const response = await authenticatedResponse(token, path, options);
  return response.blob();
}

export async function requestFile(
  token: string,
  path: string,
  options: RequestInit = {},
) {
  const response = await authenticatedResponse(token, path, options);
  return {
    blob: await response.blob(),
    disposition: response.headers.get('Content-Disposition'),
  };
}
