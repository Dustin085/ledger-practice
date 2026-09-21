package com.example.ledgerpractice.payment;

// 代表「外部服務這次連提交都失敗」（例如網路中斷、外部系統拒收）——跟之後外部服務
// 非同步回覆「轉帳失敗」是不同的失敗時機點：這個發生在 submit 當下，是 OutboxRelay
// 該留著讓下一輪重試的情況；非同步回覆失敗才是要走補償分錄的情況。
public class PaymentGatewaySubmissionException extends RuntimeException {

    public PaymentGatewaySubmissionException(String message) {
        super(message);
    }
}
