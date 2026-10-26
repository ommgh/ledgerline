// Random wallet-to-wallet transfers against a running service.
//   docker run --rm -i --network host grafana/k6 run - < loadtest/transfers.js
// Tune with -e WALLETS=200 -e RATE=500 -e DURATION=60s
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const WALLETS = Number(__ENV.WALLETS || 200);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    transfers: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 500),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    'http_req_failed{name:transfer}': ['rate<0.01'],
    'http_req_duration{name:transfer}': ['p(99)<250'],
  },
};

function post(path, body, key) {
  const headers = key ? { ...JSON_HEADERS, 'Idempotency-Key': key } : JSON_HEADERS;
  return http.post(`${BASE}${path}`, JSON.stringify(body), { headers, tags: { name: path.split('/').pop() === 'transfers' ? 'transfer' : 'setup' } });
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function setup() {
  const run = Date.now().toString(36);
  const bank = post('/api/v1/accounts', {
    code: `loadtest:bank:${run}`, name: 'Load test bank', type: 'ASSET', currency: 'INR', overdraftAllowed: true,
  }).json('id');
  const wallets = [];
  for (let i = 0; i < WALLETS; i++) {
    const id = post('/api/v1/accounts', {
      code: `loadtest:wallet:${run}:${i}`, name: `Wallet ${i}`, type: 'LIABILITY', currency: 'INR',
    }).json('id');
    post('/api/v1/transfers', { debitAccountId: bank, creditAccountId: id, amount: 1000000, currency: 'INR' }, uuid());
    wallets.push(id);
  }
  return { wallets };
}

export default function ({ wallets }) {
  const from = Math.floor(Math.random() * wallets.length);
  let to = Math.floor(Math.random() * (wallets.length - 1));
  if (to >= from) to++;
  const res = post('/api/v1/transfers', {
    debitAccountId: wallets[from],
    creditAccountId: wallets[to],
    amount: 1 + Math.floor(Math.random() * 500),
    currency: 'INR',
  }, uuid());
  check(res, { 'created': (r) => r.status === 201 });
}
