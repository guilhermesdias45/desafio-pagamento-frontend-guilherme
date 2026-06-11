export const PAYMENT_SERVICE = __ENV.PAYMENT_SERVICE_URL || 'http://localhost:8082';
export const FRAUD_SERVICE   = __ENV.FRAUD_SERVICE_URL   || 'http://localhost:8085';
export const INTERNAL_SECRET = __ENV.INTERNAL_SECRET     || 'afwh45HmU0wPZEFx01xxzYcqZWKAQVW6BuvDnP50';

export const THRESHOLDS = {
  fraud: {
    'http_req_duration': ['p(99)<200'],
    'http_req_failed': ['rate<0.01'],
  },
  payment: {
    'http_req_duration': ['p(99)<1000'],
    'http_req_failed': ['rate<0.01'],
  },
};

export function randomId() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

export function randomTxnId() {
  return 'txn_' + Math.random().toString(36).substring(2, 10);
}
