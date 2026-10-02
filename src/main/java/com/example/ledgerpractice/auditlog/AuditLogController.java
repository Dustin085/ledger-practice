package com.example.ledgerpractice.auditlog;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public String list(@PageableDefault(size = 20) Pageable pageable, Model model) {
        // 排序固定「最新在前」，不開放 sort 參數：稽核紀錄沒有需要其他排序的場景，
        // 也避免使用者傳入不存在的欄位名稱造成 500。
        Pageable newestFirst = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<AuditLog> page = auditLogRepository.findAll(newestFirst);
        model.addAttribute("page", page);
        return "pages/admin/audit-logs";
    }
}
