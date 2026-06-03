import http from 'k6/http';
import { check, sleep } from 'k6';
import { PAYMENT_SERVICE, THRESHOLDS, randomId } from './options.js';

export const options = {
  vus: 3,
  duration: '30s',
  thresholds: THRESHOLDS.payment,
};

export default function () {
  const merchantId = randomId();

  const payload = JSON.stringify({
    reason: 'CUSTOMER_REQUEST',
    requestedBy: merchantId,
    idempotencyKey: randomId(),
  });

  const res = http.post(
    `${PAYMENT_SERVICE}/api/v1/transactions/txn_test/refund`,
    payload,
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Merchant-Id': merchantId,
      },
    }
  );

  check(res, {
    'refund returns 200, 404, 422 or 403': (r) => [200, 404, 422, 403].includes(r.status),
    'has refund response structure': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(1);
}
