package com.example.ledgerpractice.ledger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LineAmountValidator implements ConstraintValidator<ValidLineAmount, LineForm> {

    @Override
    public boolean isValid(LineForm form, ConstraintValidatorContext context) {
        if (form.getDebitAmount() == null || form.getCreditAmount() == null) {
            // 必填交給 @NotNull 管，這裡不重複判斷。
            return true;
        }

        boolean debitIsZero = form.getDebitAmount().signum() == 0;
        boolean creditIsZero = form.getCreditAmount().signum() == 0;
        if (debitIsZero != creditIsZero) {
            return true;
        }

        // 讓錯誤訊息掛在 debitAmount 這個欄位上，表單才能用一般的單欄位錯誤顯示方式呈現。
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("debitAmount")
                .addConstraintViolation();
        return false;
    }
}
