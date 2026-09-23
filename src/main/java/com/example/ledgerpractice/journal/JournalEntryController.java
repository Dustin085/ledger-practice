package com.example.ledgerpractice.journal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {
    private final JournalEntryRepository journalEntryRepository;

    @GetMapping
    public String findByStatus(
            @RequestParam(name = "status", required = false) JournalEntryStatus status,
            Sort sort,
            Model model
    ) {
        Sort effectiveSort = sort.isSorted() ? sort : Sort.by(Sort.Direction.DESC, "id");
        List<JournalEntry> journalEntries = status != null
                ? journalEntryRepository.findByStatus(status, effectiveSort)
                : journalEntryRepository.findAll(effectiveSort);
        model.addAttribute("journalEntries", journalEntries);
        model.addAttribute("selectedStatus", status);
        return "pages/journal-entries/list";
    }

    @GetMapping("/{id}")
    public String findById(@PathVariable Long id, Model model) {
        JournalEntry journalEntry = journalEntryRepository.findWithLinesById(id)
                .orElseThrow(() -> new NoSuchElementException("JournalEntry with id " + id + " not found"));
        model.addAttribute("journalEntry", journalEntry);
        return "pages/journal-entries/detail";
    }
}
