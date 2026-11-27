import { useEffect, useState } from 'react';
import { ArrowDown, ArrowUp, ExternalLink, Plus, Trash2 } from 'lucide-react';
import {
  Button,
  ErrorMessage,
  Field,
  PanelHeader,
} from '../../components/WorkflowUi';
import { request } from '../../services/api';
import { body, message } from '../../services/commercial';
import { Storefront } from './Storefront';
import type { Card, Catalog } from './Storefront';

type Editor = { version: number; draft: Catalog | null; published: boolean };
const blankCard: Card = {
  title: '',
  category: '',
  description: '',
  fixedPrice: false,
  price: '',
  visible: true,
  image: '',
};
const initialCatalog: Catalog = {
  brand: 'CADPRINT',
  headline: 'Tus ideas toman forma',
  introduction: 'Detalles personalizados en impresión 3D y sublimación.',
  whatsapp: '',
  accent: '#126b60',
  cards: [
    {
      ...blankCard,
      title: 'Llavero personalizado',
      category: 'Detalles',
      fixedPrice: true,
    },
    { ...blankCard, title: 'Letrero personalizado', category: 'A medida' },
  ],
};

async function preparePhoto(file: File) {
  if (
    file.size > 15 * 1024 * 1024 ||
    !['image/png', 'image/jpeg', 'image/webp'].includes(file.type)
  ) {
    throw new Error('Elige una foto JPG, PNG o WebP de hasta 15 MB.');
  }
  const bitmap = await createImageBitmap(file);
  try {
    const canvas = document.createElement('canvas');
    const ratio = Math.min(1, 800 / Math.max(bitmap.width, bitmap.height));
    canvas.width = Math.max(1, Math.round(bitmap.width * ratio));
    canvas.height = Math.max(1, Math.round(bitmap.height * ratio));
    const context = canvas.getContext('2d');
    if (!context) throw new Error('No se pudo preparar la foto.');
    context.fillStyle = 'white';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const image = canvas.toDataURL('image/jpeg', 0.75);
    if (image.length > 350000)
      throw new Error(
        'La foto es demasiado detallada. Prueba con una más pequeña.',
      );
    return image;
  } finally {
    bitmap.close();
  }
}

export function CatalogEditor({
  token,
  onDirtyChange,
}: {
  token: string;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const [catalog, setCatalog] = useState<Catalog>();
  const [savedCatalog, setSavedCatalog] = useState<Catalog>();
  const dirty = JSON.stringify(catalog) !== JSON.stringify(savedCatalog);
  useEffect(() => {
    onDirtyChange(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);
  const [version, setVersion] = useState(0);
  const [published, setPublished] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const active = new AbortController();
    void request<Editor>(token, '/storefront-admin', {
      signal: active.signal,
    }).then(
      (saved) => {
        if (active.signal.aborted) return;
        setVersion(saved.version);
        setPublished(saved.published);
        const draft = saved.draft ?? initialCatalog;
        setCatalog(draft);
        setSavedCatalog(draft);
      },
      (failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      },
    );
    return () => active.abort();
  }, [token, reload]);

  function field<K extends keyof Catalog>(key: K, value: Catalog[K]) {
    setNotice('');
    setCatalog((previous) => previous && { ...previous, [key]: value });
  }

  function card(index: number, patch: Partial<Card>) {
    setNotice('');
    setCatalog(
      (previous) =>
        previous && {
          ...previous,
          cards: previous.cards.map((item, position) =>
            position === index ? { ...item, ...patch } : item,
          ),
        },
    );
  }

  async function photo(index: number, file?: File) {
    if (!file) return;
    setBusy(true);
    setError('');
    try {
      card(index, { image: await preparePhoto(file) });
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  async function save(publish: boolean) {
    if (!catalog || busy) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const saved = await request<Editor>(
        token,
        '/storefront-admin',
        body({ version, publish, catalog }, 'PUT'),
      );
      setVersion(saved.version);
      setPublished(saved.published);
      setSavedCatalog(catalog);
      setNotice(publish ? 'Cambios publicados.' : 'Borrador guardado.');
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  async function unpublish() {
    if (
      busy ||
      !window.confirm(
        '¿Ocultar el catálogo público? Tu borrador se conservará.',
      )
    )
      return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const saved = await request<Editor>(
        token,
        '/storefront-admin?version=' + version,
        { method: 'DELETE' },
      );
      setVersion(saved.version);
      setPublished(saved.published);
      setNotice('Catálogo oculto. Puedes volver a publicarlo cuando quieras.');
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  if (!catalog)
    return error ? (
      <section className="panel">
        <ErrorMessage>{error}</ErrorMessage>
        <Button
          onClick={() => {
            setError('');
            setReload((previous) => previous + 1);
          }}
        >
          Reintentar
        </Button>
      </section>
    ) : (
      <p role="status">Cargando editor…</p>
    );

  return (
    <section className="panel">
      <PanelHeader title="Editor del catálogo">
        <div className="catalogEditorActions">
          {published && (
            <a href="/catalogo" target="_blank" rel="noreferrer">
              Ver catálogo publicado <ExternalLink size={14} />
            </a>
          )}
          {published && (
            <Button disabled={busy} onClick={() => void unpublish()}>
              Ocultar catálogo
            </Button>
          )}
          <Button disabled={busy} onClick={() => void save(false)}>
            Guardar borrador
          </Button>
          <Button
            disabled={busy}
            tone="primary"
            onClick={() => void save(true)}
          >
            Publicar cambios
          </Button>
        </div>
      </PanelHeader>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {notice && <p role="status">{notice}</p>}
      {dirty && <p role="status">Cambios sin guardar</p>}
      <div className="catalogEditorColumns">
        <fieldset className="catalogEditorControls" disabled={busy}>
          <legend>Apariencia y contacto</legend>
          <Field label="Nombre de la marca">
            {(attrs) => (
              <input
                {...attrs}
                maxLength={60}
                value={catalog.brand}
                onChange={(e) => field('brand', e.target.value)}
              />
            )}
          </Field>
          <Field label="Título principal">
            {(attrs) => (
              <input
                {...attrs}
                maxLength={100}
                value={catalog.headline}
                onChange={(e) => field('headline', e.target.value)}
              />
            )}
          </Field>
          <Field label="Texto de bienvenida">
            {(attrs) => (
              <textarea
                {...attrs}
                maxLength={320}
                value={catalog.introduction}
                onChange={(e) => field('introduction', e.target.value)}
              />
            )}
          </Field>
          <Field label="Color de los botones">
            {(attrs) => (
              <input
                {...attrs}
                type="color"
                value={catalog.accent}
                onChange={(e) => field('accent', e.target.value)}
              />
            )}
          </Field>
          <Field label="WhatsApp con código de país">
            {(attrs) => (
              <input
                {...attrs}
                inputMode="tel"
                placeholder="502…"
                maxLength={22}
                value={catalog.whatsapp}
                onChange={(e) =>
                  field(
                    'whatsapp',
                    e.target.value.replace(/\D/g, '').slice(0, 15),
                  )
                }
              />
            )}
          </Field>
          <h3>Tarjetas del catálogo</h3>
          {catalog.cards.map((item, index) => (
            <details
              className="catalogEditorCard"
              key={index}
              open={index === 0}
            >
              <summary>
                {item.title || 'Nueva tarjeta'}
                {item.visible ? '' : ' · Oculta'}
              </summary>
              <div className="catalogEditorCardFields">
                <Field label="Nombre del producto">
                  {(attrs) => (
                    <input
                      {...attrs}
                      maxLength={100}
                      value={item.title}
                      onChange={(e) => card(index, { title: e.target.value })}
                    />
                  )}
                </Field>
                <Field label="Categoría">
                  {(attrs) => (
                    <input
                      {...attrs}
                      maxLength={60}
                      value={item.category}
                      onChange={(e) =>
                        card(index, { category: e.target.value })
                      }
                    />
                  )}
                </Field>
                <Field label="Descripción">
                  {(attrs) => (
                    <textarea
                      {...attrs}
                      maxLength={500}
                      value={item.description}
                      onChange={(e) =>
                        card(index, { description: e.target.value })
                      }
                    />
                  )}
                </Field>
                <Field label="Tipo de pedido">
                  {(attrs) => (
                    <select
                      {...attrs}
                      value={item.fixedPrice ? 'fixed' : 'quote'}
                      onChange={(e) =>
                        card(index, { fixedPrice: e.target.value === 'fixed' })
                      }
                    >
                      <option value="fixed">Precio fijo</option>
                      <option value="quote">A cotizar</option>
                    </select>
                  )}
                </Field>
                {item.fixedPrice && (
                  <Field label="Precio en quetzales">
                    {(attrs) => (
                      <input
                        {...attrs}
                        inputMode="decimal"
                        maxLength={10}
                        value={item.price}
                        onChange={(e) =>
                          card(index, {
                            price: e.target.value.replace(',', '.'),
                          })
                        }
                      />
                    )}
                  </Field>
                )}
                <Field label="Subir foto">
                  {(attrs) => (
                    <input
                      {...attrs}
                      type="file"
                      accept="image/png,image/jpeg,image/webp"
                      onChange={(e) => {
                        void photo(index, e.target.files?.[0]);
                        e.target.value = '';
                      }}
                    />
                  )}
                </Field>
                {item.image && (
                  <Button onClick={() => card(index, { image: '' })}>
                    Quitar foto
                  </Button>
                )}
                <label>
                  <input
                    type="checkbox"
                    checked={item.visible}
                    onChange={(e) => card(index, { visible: e.target.checked })}
                  />{' '}
                  Mostrar en el catálogo
                </label>
                <div className="catalogEditorActions">
                  {[-1, 1].map((direction) => (
                    <Button
                      key={direction}
                      disabled={
                        index + direction < 0 ||
                        index + direction >= catalog.cards.length
                      }
                      onClick={() => {
                        const reordered = [...catalog.cards];
                        [reordered[index], reordered[index + direction]] = [
                          reordered[index + direction],
                          reordered[index],
                        ];
                        field('cards', reordered);
                      }}
                    >
                      {direction < 0 ? (
                        <ArrowUp size={16} />
                      ) : (
                        <ArrowDown size={16} />
                      )}{' '}
                      {direction < 0 ? 'Subir' : 'Bajar'}
                    </Button>
                  ))}
                  <Button
                    tone="danger"
                    onClick={() =>
                      field(
                        'cards',
                        catalog.cards.filter(
                          (_, position) => position !== index,
                        ),
                      )
                    }
                  >
                    <Trash2 size={16} /> Quitar tarjeta
                  </Button>
                </div>
              </div>
            </details>
          ))}
          <Button
            disabled={catalog.cards.length >= 12}
            onClick={() => field('cards', [...catalog.cards, { ...blankCard }])}
          >
            <Plus size={18} /> Agregar tarjeta
          </Button>
        </fieldset>
        <div className="catalogPreview">
          <Storefront catalog={catalog} preview />
        </div>
      </div>
    </section>
  );
}
