import { useState } from 'react';
import { LoginPage } from './features/LoginPage';
import { PublicStorefrontPage } from './features/storefront/Storefront';
import { Workspace } from './features/Workspace';
import { request } from './services/api';

const sessionKey = 'cadprintAuth';

export default function App() {
  return window.location.pathname.replace(/\/$/, '') === '/catalogo' ? (
    <PublicStorefrontPage />
  ) : (
    <Manager />
  );
}

function Manager() {
  const [token, setToken] = useState(
    () => sessionStorage.getItem(sessionKey) || '',
  );

  async function login(nextToken: string) {
    await request(nextToken, '/me');
    sessionStorage.setItem(sessionKey, nextToken);
    setToken(nextToken);
  }

  function logout() {
    sessionStorage.removeItem(sessionKey);
    setToken('');
  }

  return token ? (
    <Workspace token={token} onLogout={logout} />
  ) : (
    <LoginPage onLogin={login} />
  );
}
