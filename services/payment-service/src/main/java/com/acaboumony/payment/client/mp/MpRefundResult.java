package com.acaboumony.payment.client.mp;

public sealed interface MpRefundResult
        permits MpRefundResult.Success, MpRefundResult.Failure {

    record Success(long mpRefundId) implements MpRefundResult {}

    record Failure(String detail) implements MpRefundResult {}
}
