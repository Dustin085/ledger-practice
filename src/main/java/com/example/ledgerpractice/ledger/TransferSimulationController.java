package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.outbox.TransferResult;
import com.example.ledgerpractice.outbox.TransferResultMessage;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import com.example.ledgerpractice.webhook.WebhookSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

// 這個 controller 扮演「外部金流系統」：畫面上的按鈕會用 HTTP 打我們自己的
// /webhooks/settlement，跟真實外部系統打 callback 的路徑完全一樣，事件 id 與簽章都由這一端決定。
@Controller
@RequestMapping("/transfers")
public class TransferSimulationController {
    private final FundTransferRequestRepository fundTransferRequestRepository;
    private final WebhookSigner webhookSigner;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TransferSimulationController(
            FundTransferRequestRepository fundTransferRequestRepository,
            WebhookSigner webhookSigner,
            ObjectMapper objectMapper,
            @Value("http://localhost:${server.port:8080}") String selfBaseUrl) {
        this.fundTransferRequestRepository = fundTransferRequestRepository;
        this.webhookSigner = webhookSigner;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(selfBaseUrl).build();
    }

    @GetMapping("/pending")
    public String findPendingTransferRequests(Model model) {
        List<FundTransferRequest> fundTransferRequests = fundTransferRequestRepository.findByStatus(TransferStatus.SUBMITTED);
        model.addAttribute("pendingTransferRequests", fundTransferRequests);
        return "pages/fund-transfer-requests/list";
    }

    @PostMapping("/{externalReferenceId}/confirm")
    public String confirmTransfer(@PathVariable String externalReferenceId, RedirectAttributes redirectAttributes) {
        callWebhook(externalReferenceId, TransferResult.CONFIRMED, "確認", redirectAttributes);
        return "redirect:/transfers/pending";
    }

    @PostMapping("/{externalReferenceId}/fail")
    public String failTransfer(@PathVariable String externalReferenceId, RedirectAttributes redirectAttributes) {
        callWebhook(externalReferenceId, TransferResult.FAILED, "失敗", redirectAttributes);
        return "redirect:/transfers/pending";
    }

    private void callWebhook(String externalReferenceId, TransferResult result, String label, RedirectAttributes redirectAttributes) {
        String body = objectMapper.writeValueAsString(
                new TransferResultMessage(externalReferenceId, UUID.randomUUID().toString(), result));
        try {
            restClient.post()
                    .uri("/webhooks/settlement")
                    .header(WebhookSigner.SIGNATURE_HEADER, webhookSigner.sign(body))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            redirectAttributes.addFlashAttribute("infoMessage", "已送出「" + label + "」通知，處理是非同步的，稍後重新整理查看結果");
        } catch (RestClientException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "webhook 呼叫失敗：" + e.getMessage());
        }
    }
}
