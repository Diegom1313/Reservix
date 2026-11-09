import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SESSION = Number(__ENV.SESSION_ID);

export const options = { vus: 100, iterations: 300 };

export default function () {
  const headers = { 'Content-Type': 'application/json' };
  const email = `k6-${__VU}-${__ITER}@carga.test`;
  const registro = http.post(`${BASE}/api/auth/register`, JSON.stringify({ name: 'Carga', email, password: 'clave-de-carga-2026' }), { headers });
  const token = registro.json('token');
  const res = http.post(`${BASE}/api/bookings`, JSON.stringify({ sessionId: SESSION }), { headers: { ...headers, Authorization: `Bearer ${token}` } });
  check(res, { 'creada o sin plazas': (r) => r.status === 201 || r.status === 409 });
}
