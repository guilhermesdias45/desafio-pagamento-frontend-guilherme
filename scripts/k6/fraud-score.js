import http from 'k6/http';
import { check, sleep } from 'k6';
import { FRAUD_SERVICE, INTERNAL_SECRET, THRESHOLDS, randomId } from './options.js';

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: THRESHOLDS.fraud,
};

const SCENARIOS = [
  { decision: 'APPROVE', customerMultiplier: 1, merchantMultiplier: 1 },
  { decision: 'BLOCK', customerMultiplier: 6, merchantMultiplier: 1 },
  { decision: 'REVIEW', customerMultiplier: 3, merchantMultiplier: 5 },
];

export default function () {
  const scenario = SCENARIOS[Math.floor(Math.random() * SCENARIOS.length)];
  const customerId = randomId();
  const merchantId = randomId();

  for (let i = 0; i < scenario.customerMultiplier; i++) {
    const payload = JSON.stringify({
      transactionId: randomId(),
      customerId: customerId,
      merchantId: merchantId,
      amountInCents: 5000,
      paymentMethodId: 'visa',
      ipAddress: `192.168.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`,
      deviceFingerprint: scenario.decision === 'BLOCK' ? 'suspicious-device' : 'known-device',
    });

    const res = http.post(`${FRAUD_SERVICE}/internal/fraud/score`, payload, {
      headers: {
        'Content-Type': 'application/json',
        'X-Internal-Secret': INTERNAL_SECRET,
      },
    });

    check(res, {
      'status is 200': (r) => r.status === 200,
      'has decision': (r) => {
        const body = JSON.parse(r.body);
        return ['APPROVE', 'BLOCK', 'REVIEW'].includes(body.decision);
      },
    });
  }

  sleep(0.5);
}
