import { useCallback, useEffect, useState } from 'react';

const ESTADOS = { PENDING_PAYMENT: 'Pendiente de pago', CONFIRMED: 'Confirmada', CANCELLED: 'Cancelada', EXPIRED: 'Caducada' };
const euros = (c) => (c / 100).toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
const fecha = (v) => new Date(v).toLocaleString('es-ES', { dateStyle: 'full', timeStyle: 'short' });

async function api(metodo, ruta, cuerpo, token) {
  const headers = {};
  if (cuerpo) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`/api${ruta}`, { method: metodo, headers, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  if (res.status === 204) return null;
  const datos = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(datos.detail || 'No se pudo completar la acción. Revisa los datos.');
  return datos;
}

function Acceso({ onToken }) {
  const [modo, setModo] = useState('login');
  const [f, setF] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState('');
  const cambiar = (e) => setF({ ...f, [e.target.name]: e.target.value });

  async function enviar(e) {
    e.preventDefault();
    setError('');
    try {
      const r = await api('POST', modo === 'login' ? '/auth/login' : '/auth/register', modo === 'login' ? { email: f.email, password: f.password } : f);
      onToken(r.token);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <form className="acceso" onSubmit={enviar}>
      <h2>{modo === 'login' ? 'Entra para reservar' : 'Crea tu cuenta'}</h2>
      {modo === 'registro' && <label>Nombre<input name="name" value={f.name} onChange={cambiar} required /></label>}
      <label>Correo<input name="email" type="email" value={f.email} onChange={cambiar} required /></label>
      <label>Contraseña<input name="password" type="password" minLength={modo === 'registro' ? 12 : 1} value={f.password} onChange={cambiar} required /></label>
      {error && <p className="error" role="alert">{error}</p>}
      <button>{modo === 'login' ? 'Entrar' : 'Crear cuenta'}</button>
      <button type="button" className="enlace" onClick={() => setModo(modo === 'login' ? 'registro' : 'login')}>
        {modo === 'login' ? 'Crear una cuenta nueva' : 'Ya tengo cuenta'}
      </button>
    </form>
  );
}

export default function App() {
  const [token, setToken] = useState(null);
  const [clases, setClases] = useState([]);
  const [mias, setMias] = useState([]);
  const [aviso, setAviso] = useState('');

  const cargar = useCallback(async () => {
    setClases(await api('GET', '/sessions'));
    if (token) setMias(await api('GET', '/bookings/mine', null, token));
  }, [token]);

  useEffect(() => {
    cargar().catch((e) => setAviso(e.message));
  }, [cargar]);

  async function accion(fn) {
    setAviso('');
    try {
      await fn();
    } catch (e) {
      setAviso(e.message);
    }
    await cargar().catch(() => {});
  }

  const reservar = (id) => accion(async () => {
    const r = await api('POST', '/bookings', { sessionId: id }, token);
    window.location.assign(r.paymentUrl);
  });
  const cancelar = (id) => accion(() => api('DELETE', `/bookings/${id}`, null, token));

  return (
    <main>
      <header><h1>Reservix</h1>{token && <button className="enlace" onClick={() => setToken(null)}>Salir</button>}</header>
      {aviso && <p className="error" role="alert">{aviso}</p>}
      {!token && <Acceso onToken={setToken} />}
      <h2>Próximas clases</h2>
      <ul className="lista">
        {clases.map((c) => (
          <li key={c.id}>
            <div><b>{c.title}</b><small>{fecha(c.startsAt)}</small></div>
            <span>{c.availableSeats} de {c.capacity} plazas</span>
            <span>{euros(c.priceCents)}</span>
            {token && <button disabled={c.availableSeats < 1} onClick={() => reservar(c.id)}>{c.availableSeats < 1 ? 'Completa' : 'Reservar'}</button>}
          </li>
        ))}
        {!clases.length && <li>No hay clases programadas.</li>}
      </ul>
      {token && (
        <>
          <h2>Mis reservas</h2>
          <ul className="lista">
            {mias.map((b) => (
              <li key={b.id}>
                <b>{b.sessionTitle}</b>
                <span>{ESTADOS[b.status]}</span>
                {['PENDING_PAYMENT', 'CONFIRMED'].includes(b.status) && <button className="enlace" onClick={() => cancelar(b.id)}>Cancelar</button>}
              </li>
            ))}
            {!mias.length && <li>Todavía no tienes reservas.</li>}
          </ul>
        </>
      )}
    </main>
  );
}
