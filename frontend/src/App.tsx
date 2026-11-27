import { useState } from 'react';
import { LoginPage } from './features/LoginPage';
import { Workspace } from './features/Workspace';
import { request } from './services/api';

const sessionKey = 'cadprintAuth';

export default function App() {
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
