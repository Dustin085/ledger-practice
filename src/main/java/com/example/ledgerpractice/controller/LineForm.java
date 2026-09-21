package com.example.ledgerpractice.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 表單綁定專用的可變 bean，不是 entity，也不是 service 用的 JournalLineRequest——
// Thymeleaf th:field 的雙向綁定要靠 setter，record 的建構子綁定做不到逐欄位寫回。
@Setter
@Getter
public class LineForm {

    @NotNull(message = "請選擇科目")
    private Long accountId;

    @NotNull
    @PositiveOrZero(message = "借方金額不能是負數")
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero(message = "貸方金額不能是負數")
    private BigDecimal creditAmount = BigDecimal.ZERO;

    private String memo;

}
