package com.example.ledgerpractice.controller;

import com.example.ledgerpractice.ledger.TransferResultService;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferSimulationController {
    private final FundTransferRequestRepository fundTransferRequestRepository;
    private final TransferResultService transferResultService;

    @GetMapping("/pending")
    public String findPendingTransferRequests(Model model) {
        List<FundTransferRequest> fundTransferRequests = fundTransferRequestRepository.findByStatus(TransferStatus.SUBMITTED);
        model.addAttribute("pendingTransferRequests", fundTransferRequests);
        return "pages/fund-transfer-requests/list";
    }

    @PostMapping("/{externalReferenceId}/confirm")
    public String confirmTransfer(@PathVariable String externalReferenceId, RedirectAttributes redirectAttributes) {
        try {
            transferResultService.confirm(externalReferenceId, UUID.randomUUID().toString());
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/transfers/pending";
    }

    @PostMapping("/{externalReferenceId}/fail")
    public String failTransfer(@PathVariable String externalReferenceId, RedirectAttributes redirectAttributes) {
        try {
            transferResultService.fail(externalReferenceId, UUID.randomUUID().toString());
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/transfers/pending";
    }
}
