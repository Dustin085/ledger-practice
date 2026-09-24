package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.outbox.TransferResult;
import com.example.ledgerpractice.outbox.TransferResultMessage;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

import static com.example.ledgerpractice.outbox.RabbitConfig.EXTERNAL_SETTLEMENT_EXCHANGE_NAME;
import static com.example.ledgerpractice.outbox.RabbitConfig.TRANSFER_CONFIRMED_ROUTING_KEY;
import static com.example.ledgerpractice.outbox.RabbitConfig.TRANSFER_FAILED_ROUTING_KEY;

// 這個 controller 扮演「外部系統」：它決定事件 id 並把結果發布出去，
// 我們自己的 TransferResultListener 才是接收端（Inbox 那一側）。
@Controller
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferSimulationController {
    private final FundTransferRequestRepository fundTransferRequestRepository;
    private final RabbitTemplate rabbitTemplate;

    @GetMapping("/pending")
    public String findPendingTransferRequests(Model model) {
        List<FundTransferRequest> fundTransferRequests = fundTransferRequestRepository.findByStatus(TransferStatus.SUBMITTED);
        model.addAttribute("pendingTransferRequests", fundTransferRequests);
        return "pages/fund-transfer-requests/list";
    }

    @PostMapping("/{externalReferenceId}/confirm")
    public String confirmTransfer(@PathVariable String externalReferenceId, RedirectAttributes redirectAttributes) {
        publishResult(externalReferenceId, TransferResult.CONFIRMED, TRANSFER_CONFIRMED_ROUTING_KEY);
        redirectAttributes.addFlashAttribute("infoMessage", "已送出「確認」通知，處理是非同步的，稍後重新整理查看結果");
        return "redirect:/transfers/pending";
    }

    @PostMapping("/{externalReferenceId}/fail")
    public String failTransfer(@PathVariable String externalReferenceId, RedirectAttributes redirectAttributes) {
        publishResult(externalReferenceId, TransferResult.FAILED, TRANSFER_FAILED_ROUTING_KEY);
        redirectAttributes.addFlashAttribute("infoMessage", "已送出「失敗」通知，處理是非同步的，稍後重新整理查看結果");
        return "redirect:/transfers/pending";
    }

    private void publishResult(String externalReferenceId, TransferResult result, String routingKey) {
        rabbitTemplate.convertAndSend(
                EXTERNAL_SETTLEMENT_EXCHANGE_NAME,
                routingKey,
                new TransferResultMessage(externalReferenceId, UUID.randomUUID().toString(), result));
    }
}
