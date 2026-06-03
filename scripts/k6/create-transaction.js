import http from 'k6/http';
import { check, sleep } from 'k6';
import { PAYMENT_SERVICE, THRESHOLDS, randomId, randomTxnId } from './options.js';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: THRESHOLDS.payment,
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
      'X-Customer-Email': 'cliente@teste.com',
      'X-Merchant-Id': merchantId,
      'X-Forwarded-For': `192.168.1.${Math.floor(Math.random() * 254) + 1}`,
    },
  });

  check(res, {
    'status is 201 or 422 or 429': (r) => [201, 422, 429].includes(r.status),
    'has transactionId in response': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.transactionId !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(1);
}
