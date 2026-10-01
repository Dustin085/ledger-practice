package com.example.ledgerpractice.report;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TrialBalanceMapper {
    // asOfDate 為 null 代表不限日期（維持原本「全部歷史」的行為）。
    List<TrialBalanceRow> findTrialBalance(@Param("asOfDate") LocalDate asOfDate);
}
