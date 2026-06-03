import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomId } from './options.js';

const API_GATEWAY = 'http://localhost:8080';

export const options = {
  vus: 3,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(99)<2000'],
    http_req_failed: ['rate<0.05'],
  },
};

const TOKEN_32_HEX = 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6';

export default function () {
  const customerId = randomId();
  const merchantId = randomId();
  const orderId = randomId();

  const loginRes = http.post(`${API_GATEWAY}/api/v1/auth/login`, JSON.stringify({
    email: 'cliente@teste.com',
    password: 'senha123',
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(loginRes, {
    'login returns 200 or 401': (r) => [200, 401].includes(r.status),
  });

  const authToken = loginRes.status === 200
    ? JSON.parse(loginRes.body).token
    : 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwicm9sZSI6IkNVU1RPTUVSIn0.fake';

  const txnPayload = JSON.stringify({
    amountInCents: 8990,
    currency: 'BRL',
    customerId: customerId,
    orderId: orderId,
    cardToken: TOKEN_32_HEX,
    paymentMethodId: 'visa',
    installments: 1,
    idempotencyKey: randomId(),
  });

  const txnRes = http.post(`${API_GATEWAY}/api/v1/transactions`, txnPayload, {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${authToken}`,
      'X-Forwarded-For': '10.0.0.1',
    },
  });

  check(txnRes, {
    'transaction returns 201, 422 or 401': (r) => [201, 422, 401].includes(r.status),
  });

  const transactionId = txnRes.status === 201
    ? JSON.parse(txnRes.body).data.transactionId
    : 'txn_unknown';

  const getRes = http.get(`${API_GATEWAY}/api/v1/transactions/${transactionId}`, {
    headers: { 'Authorization': `Bearer ${authToken}` },
  });

  check(getRes, {
    'get transaction returns 200 or 401': (r) => [200, 401].includes(r.status),
  });

  sleep(2);
}
