package com.example.ledgerpractice.report;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TrialBalanceMapper {
    List<TrialBalanceRow> findTrialBalance();
}
