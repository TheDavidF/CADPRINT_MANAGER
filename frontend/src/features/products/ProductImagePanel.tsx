import { useEffect, useRef, useState } from 'react';
import { catalogError, getProductImage } from './productApi';
import type { Product, ProductImage } from './productTypes';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

export function ProductImagePanel({
  token,
  product,
  image,
  admin,
  busy,
  onUpload,
  onRemove,
}: {
  token: string;
  product: Product;
  image: ProductImage | null;
  admin: boolean;
  busy: boolean;
  onUpload: (file: File) => Promise<boolean>;
  onRemove: () => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [selection, setSelection] = useState<File | null>(null);
  const [validation, setValidation] = useState('');
  const [preview, setPreview] = useState<{
    key: string;
    url?: string;
    error?: string;
  }>();
  const [attempt, setAttempt] = useState(0);
  const id = product.id;
  const imageKey = image ? id + ':' + image.createdAt : null;
  const previewKey = imageKey ? imageKey + ':' + attempt : null;

  useEffect(() => {
    if (!previewKey) return;
    const controller = new AbortController();
    let url: string | undefined;
    void getProductImage(token, id, controller.signal)
      .then((blob) => {
        if (controller.signal.aborted) return;
        url = URL.createObjectURL(blob);
        setPreview({ key: previewKey, url });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setPreview({ key: previewKey, error: catalogError(error) });
      });
    return () => {
      controller.abort();
      if (url) URL.revokeObjectURL(url);
    };
  }, [token, id, previewKey]);

  const current = preview?.key === previewKey ? preview : undefined;
  return (
    <section>
      <h2>Imagen principal</h2>
      {image && !current && <p role="status">Cargando imagen…</p>}
      {image && current?.error && (
        <div className="notice">
          <span role="alert">{current.error}</span>
          <button onClick={() => setAttempt((value) => value + 1)}>
            Reintentar vista previa
          </button>
        </div>
      )}
      {image && current?.url && (
        <img
          className="catalogImage"
          src={current.url}
          alt={'Imagen principal de ' + product.name}
        />
      )}
      {!image && <p>El producto aún no tiene imagen.</p>}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (!selection) return;
          void onUpload(selection).then((saved) => {
            if (saved) {
              setSelection(null);
              if (input.current) input.current.value = '';
            }
          });
        }}
      >
        <label className="catalogFile">
          Seleccionar JPEG o PNG
          <input
            ref={input}
            type="file"
            accept="image/jpeg,image/png"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];
              let error = '';
              if (file && file.size > MAX_IMAGE_BYTES)
                error = 'La imagen supera el límite de 5 MiB.';
              else if (
                file &&
                (!file.size || !['image/jpeg', 'image/png'].includes(file.type))
              )
                error = 'Selecciona un JPEG o PNG válido.';
              setValidation(error);
              setSelection(error ? null : (file ?? null));
            }}
          />
        </label>
        {validation && (
          <p className="notice" role="alert">
            {validation}
          </p>
        )}
        <div className="purchaseActions">
          <button type="submit" disabled={busy || !selection}>
            {busy
              ? 'Procesando…'
              : image
                ? 'Reemplazar imagen'
                : 'Cargar imagen'}
          </button>
          {admin && image && (
            <button type="button" disabled={busy} onClick={onRemove}>
              Retirar imagen
            </button>
          )}
        </div>
      </form>
    </section>
  );
}
