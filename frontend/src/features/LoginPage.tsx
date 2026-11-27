import { useState, type FormEvent } from 'react';

export function LoginPage({
  onLogin,
}: {
  onLogin: (token: string) => Promise<void>;
}) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage('');
    try {
      const bytes = new TextEncoder().encode(username + ':' + password);
      await onLogin(
        btoa(Array.from(bytes, (byte) => String.fromCharCode(byte)).join('')),
      );
    } catch {
      setMessage('Credenciales incorrectas o servidor no disponible');
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="login">
      <form className="loginCard" onSubmit={login}>
        <div className="brand">
          ⬡ CADPRINT <span>Manager</span>
        </div>
        <h2>Iniciar sesión</h2>
        <label>
          Usuario
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </label>
        <label>
          Contraseña
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button disabled={busy}>Ingresar</button>
        {message && <p className="notice">{message}</p>}
        <small>Acceso interno · Entorno de desarrollo local</small>
      </form>
    </main>
  );
}
