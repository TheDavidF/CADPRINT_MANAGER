import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import { ArrowUpRight, ImagePlus, MessageCircle } from 'lucide-react';
import { ApiError, request } from '../../services/api';
import { gtq } from '../../services/commercial';
import './storefront.css';

export type Card = {
  title: string;
  category: string;
  description: string;
  fixedPrice: boolean;
  price: string;
  visible: boolean;
  image: string;
};

export type Catalog = {
  brand: string;
  headline: string;
  introduction: string;
  whatsapp: string;
  accent: string;
  cards: Card[];
};

function foreground(color: string) {
  const channels = [1, 3, 5].map((offset) => {
    const value = parseInt(color.slice(offset, offset + 2), 16) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722 >
    0.179
    ? '#111315'
    : '#ffffff';
}

export function Storefront({
  catalog,
  preview = false,
}: {
  catalog: Catalog;
  preview?: boolean;
}) {
  const Content = preview ? 'div' : 'main';
  const theme = {
    '--catalog-accent': catalog.accent,
    '--catalog-on-accent': foreground(catalog.accent),
  } as CSSProperties;
  return (
    <div className="publicCatalog" style={theme}>
      <header className="publicCatalogHeader">
        <span className="publicCatalogBrand">{catalog.brand}</span>
        {catalog.whatsapp && !preview && (
          <a
            href={'https://wa.me/' + catalog.whatsapp}
            target="_blank"
            rel="noreferrer"
          >
            <MessageCircle size={18} /> WhatsApp
          </a>
        )}
      </header>
      <Content className="publicCatalogMain">
        <div className="publicCatalogIntro">
          <h1>{catalog.headline}</h1>
          {catalog.introduction && <p>{catalog.introduction}</p>}
        </div>
        <div className="publicCatalogGrid">
          {catalog.cards
            .filter((card) => card.visible)
            .map((card, index) => {
              const copy = card.fixedPrice
                ? 'Pedir por WhatsApp'
                : 'Solicitar cotización';
              const greeting = card.fixedPrice
                ? 'Hola, me interesa pedir ' + card.title + '.'
                : 'Hola, quisiera cotizar ' + card.title + '.';
              return (
                <article className="publicCatalogCard" key={index}>
                  <div className="publicCatalogPhoto">
                    {card.image ? (
                      <img alt={card.title} src={card.image} />
                    ) : (
                      <ImagePlus size={30} aria-hidden />
                    )}
                  </div>
                  <div className="publicCatalogCardBody">
                    {card.category && (
                      <p className="publicCatalogCategory">{card.category}</p>
                    )}
                    <h2>{card.title}</h2>
                    {card.description && <p>{card.description}</p>}
                    <strong>
                      {card.fixedPrice && /^\d+(?:\.\d{1,2})?$/.test(card.price)
                        ? gtq(card.price)
                        : 'A cotizar'}
                    </strong>
                    {!preview && catalog.whatsapp ? (
                      <a
                        className="catalogAction"
                        target="_blank"
                        rel="noreferrer"
                        href={
                          'https://wa.me/' +
                          catalog.whatsapp +
                          '?text=' +
                          encodeURIComponent(greeting)
                        }
                      >
                        {copy} <ArrowUpRight size={16} />
                      </a>
                    ) : (
                      <span className="catalogAction">
                        {copy} <ArrowUpRight size={16} />
                      </span>
                    )}
                  </div>
                </article>
              );
            })}
        </div>
      </Content>
    </div>
  );
}

export function PublicStorefrontPage() {
  const [catalog, setCatalog] = useState<Catalog>();
  const [status, setStatus] = useState('Cargando catálogo…');
  useEffect(() => {
    const controller = new AbortController();
    void request<Catalog>('', '/storefront', {
      signal: controller.signal,
    }).then(
      (data) => {
        if (!controller.signal.aborted) setCatalog(data);
      },
      (failure: unknown) => {
        if (controller.signal.aborted) return;
        setStatus(
          failure instanceof ApiError && failure.status === 404
            ? 'El catálogo aún no está disponible.'
            : 'No se pudo cargar el catálogo. Vuelve a intentarlo.',
        );
      },
    );
    return () => controller.abort();
  }, []);
  return catalog ? (
    <Storefront catalog={catalog} />
  ) : (
    <div className="publicCatalog publicCatalogEmpty">
      <h1>CADPRINT</h1>
      <p role="status">{status}</p>
    </div>
  );
}
