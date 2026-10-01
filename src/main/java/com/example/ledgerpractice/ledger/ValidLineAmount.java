package com.example.ledgerpractice.ledger;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 標在 class 上（不是單一欄位），因為要同時讀 debitAmount 跟 creditAmount 兩個欄位才能判斷。
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LineAmountValidator.class)
public @interface ValidLineAmount {
    String message() default "借方與貸方金額必須恰好一個有值，不能同時有值或同時是 0";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
