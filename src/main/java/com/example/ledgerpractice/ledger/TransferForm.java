package com.example.ledgerpractice.ledger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class TransferForm {

    @NotNull(message = "請選擇日期")
    private LocalDate entryDate = LocalDate.now();

    @NotBlank(message = "請輸入摘要")
    private String description;

    // 純內部分錄用不到這欄，故意不加 @NotBlank——要不要填由 LedgerService 依 lines
    // 裡有沒有 externalSettlement 科目來決定，不是表單層級能判斷的事。
    private String externalCounterparty;

    @NotEmpty(message = "至少要有一筆分錄明細")
    @Valid
    private List<LineForm> lines = new ArrayList<>();

}
