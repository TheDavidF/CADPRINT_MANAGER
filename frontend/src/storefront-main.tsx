import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { PublicStorefrontPage } from './features/storefront/Storefront';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <PublicStorefrontPage />
  </StrictMode>,
);
