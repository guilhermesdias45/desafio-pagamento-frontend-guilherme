import http from 'k6/http';
import { check, sleep } from 'k6';
import { PAYMENT_SERVICE, THRESHOLDS, randomId } from './options.js';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    'http_req_duration': ['p(99)<1000'],
  },
};

export default function () {
  const merchantId = randomId();
  const customerId = randomId();

  const listRes = http.get(
    `${PAYMENT_SERVICE}/api/v1/transactions?customerId=${customerId}&page=0&size=10`,
    {
      headers: { 'X-Merchant-Id': merchantId },
    }
  );

  check(listRes, {
    'list status is 200': (r) => r.status === 200,
    'list returns data array': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.data);
      } catch (e) {
        return false;
      }
    },
  });

  const detailRes = http.get(
    `${PAYMENT_SERVICE}/api/v1/transactions/txn_test`,
    {
      headers: { 'X-Merchant-Id': merchantId },
    }
  );

  check(detailRes, {
    'detail returns 200, 403 ou 404': (r) => [200, 403, 404].includes(r.status),
  });

  sleep(0.5);
}
