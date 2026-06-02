package com.acaboumony.fraud.rules;

/**
 * Contextual data loaded from Redis before evaluating fraud rules.
 *
 * @param transactionsInLast5MinForCustomer  Number of transactions seen for this customer
 *                                           in the last 5-minute sliding window.
 * @param averageAmountLast30DaysInCents     Average transaction amount over the last 30 days.
 *                                           Zero when no historical data exists (new customer).
 * @param ipInBlacklist                      True if the request IP is in the Redis blacklist.
 * @param devicesLinkedToCardHash            Number of distinct devices linked to this payment method.
 * @param isFirstPurchase                    True if velocity counter was 0 before this transaction
 *                                           (proxy for first-time buyer detection).
 */
public record FraudRuleContext(
        long transactionsInLast5MinForCustomer,
        long averageAmountLast30DaysInCents,
        boolean ipInBlacklist,
        long devicesLinkedToCardHash,
        boolean isFirstPurchase
) {}
