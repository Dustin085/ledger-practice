package com.example.ledgerpractice.journal;

import com.example.ledgerpractice.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

import static com.example.ledgerpractice.journal.JournalEntrySpecifications.entryDateFrom;
import static com.example.ledgerpractice.journal.JournalEntrySpecifications.entryDateTo;
import static com.example.ledgerpractice.journal.JournalEntrySpecifications.hasKeyword;
import static com.example.ledgerpractice.journal.JournalEntrySpecifications.hasStatus;

@Controller
@RequestMapping("/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {
    private final JournalEntryRepository journalEntryRepository;

    @GetMapping
    public String findByStatus(
            @RequestParam(name = "status", required = false) JournalEntryStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault Pageable pageable,
            Model model
    ) {
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
        Specification<JournalEntry> spec = Specification
                .where(hasStatus(status))
                .and(hasKeyword(keyword))
                .and(entryDateFrom(from))
                .and(entryDateTo(to));
        Page<JournalEntry> page = journalEntryRepository.findAll(spec, effectivePageable);

        // page 超出範圍（例如篩選條件改變後，總頁數變少了）：導去最後一頁，
        // 而不是顯示一個數字對不上、內容是空的頁面。
        if (page.getTotalPages() > 0 && effectivePageable.getPageNumber() >= page.getTotalPages()) {
            UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/journal-entries")
                    .queryParam("page", page.getTotalPages() - 1)
                    .queryParam("size", effectivePageable.getPageSize());
            if (status != null) {
                redirect.queryParam("status", status);
            }
            if (keyword != null) {
                redirect.queryParam("keyword", keyword);
            }
            if (from != null) {
                redirect.queryParam("from", from);
            }
            if (to != null) {
                redirect.queryParam("to", to);
            }
            if (effectivePageable.getSort().isSorted()) {
                redirect.queryParam("sort",
                        effectivePageable.getSort().stream()
                                .map(order -> order.getProperty() + "," + order.getDirection())
                                .toArray());
            }
            return "redirect:" + redirect.build().toUriString();
        }

        model.addAttribute("page", page);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "pages/journal-entries/list";
    }

    @GetMapping("/{id}")
    public String findById(@PathVariable Long id, Model model) {
        JournalEntry journalEntry = journalEntryRepository.findWithLinesById(id)
                .orElseThrow(() -> new EntityNotFoundException(JournalEntry.class, id));
        model.addAttribute("journalEntry", journalEntry);
        return "pages/journal-entries/detail";
    }
}
