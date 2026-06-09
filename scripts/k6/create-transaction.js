import http from 'k6/http';
import { check, sleep } from 'k6';
import { PAYMENT_SERVICE, THRESHOLDS, randomId, randomTxnId } from './options.js';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    // UUIDs aleatórios geram 404/503 esperados — medir só latência
    'http_req_duration': ['p(99)<1000'],
  },
};

const TOKEN_32_HEX = 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6';

export default function () {
  const customerId = randomId();
  const merchantId = randomId();
  const orderId = randomId();

  const payload = JSON.stringify({
    amountInCents: 8990,
    currency: 'BRL',
    customerId: customerId,
    orderId: orderId,
    cardToken: TOKEN_32_HEX,
    paymentMethodId: 'visa',
    installments: 1,
    idempotencyKey: randomId(),
  });

  const res = http.post(`${PAYMENT_SERVICE}/api/v1/transactions`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-User-Email': 'cliente@teste.com',
      'X-Merchant-Id': merchantId,
      'X-Forwarded-For': `192.168.1.${Math.floor(Math.random() * 254) + 1}`,
    },
  });

  check(res, {
    // 201=aprovado, 200=duplicata, 404=order/customer não encontrado (UUIDs aleatórios),
    // 422=fraude/cartão recusado, 429=rate limit, 503=circuit breaker aberto
    'status esperado': (r) => [200, 201, 404, 422, 429, 503].includes(r.status),
    'body é JSON válido': (r) => {
      try { JSON.parse(r.body); return true; } catch (e) { return false; }
    },
  });

  sleep(1);
}
