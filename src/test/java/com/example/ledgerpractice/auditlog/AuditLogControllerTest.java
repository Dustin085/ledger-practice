package com.example.ledgerpractice.auditlog;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @Test
    void listShowsEntriesNewestFirst() throws Exception {
        AuditLog entry = AuditLog.builder()
                .id(1L)
                .aggregateType("FundTransferRequest")
                .aggregateId(42L)
                .eventType("FundTransferRequested")
                .payload("{\"transferRequestId\":42}")
                .build();
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entry)));

        mockMvc.perform(get("/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/audit-logs"))
                .andExpect(model().attributeExists("page"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FundTransferRequested")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("transferRequestId")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Test
    void listShowsEmptyMessageWhenNoEntries() throws Exception {
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("目前沒有稽核紀錄")));
    }

    @Test
    void sortParameterFromQueryStringIsIgnored() throws Exception {
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/audit-logs").param("sort", "noSuchField,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
