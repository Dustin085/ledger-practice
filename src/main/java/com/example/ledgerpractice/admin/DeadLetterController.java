package com.example.ledgerpractice.admin;

import com.example.ledgerpractice.outbox.DeadLetterQueueService;
import com.example.ledgerpractice.outbox.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {
    private static final Map<String, String> DLQ_NAMES = Map.of(
            "submission", RabbitConfig.SUBMISSION_DEAD_LETTER_QUEUE_NAME,
            "inbox", RabbitConfig.TRANSFER_RESULT_DEAD_LETTER_QUEUE_NAME);

    private final DeadLetterQueueService deadLetterQueueService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("submissionCount", deadLetterQueueService.countMessages(DLQ_NAMES.get("submission")));
        model.addAttribute("submissionNext", deadLetterQueueService.peekNext(DLQ_NAMES.get("submission")).orElse(null));
        model.addAttribute("inboxCount", deadLetterQueueService.countMessages(DLQ_NAMES.get("inbox")));
        model.addAttribute("inboxNext", deadLetterQueueService.peekNext(DLQ_NAMES.get("inbox")).orElse(null));
        return "pages/admin/dead-letters";
    }

    @PostMapping("/{dlqKey}/reprocess")
    public String reprocess(@PathVariable String dlqKey, RedirectAttributes redirectAttributes) {
        String dlqName = DLQ_NAMES.get(dlqKey);
        if (dlqName == null) {
            redirectAttributes.addFlashAttribute("errorMessage","無效的 dlqKey");
            return "redirect:/admin/dead-letters";
        }
        boolean processed = deadLetterQueueService.reprocessNext(dlqName);
        redirectAttributes.addFlashAttribute(processed ? "infoMessage" : "errorMessage",
                processed ? "已重新送出一筆訊息" : "佇列目前沒有訊息");
        return "redirect:/admin/dead-letters";
    }

    @PostMapping("/{dlqKey}/discard")
    public String discard(@PathVariable String dlqKey, RedirectAttributes redirectAttributes) {
        String dlqName = DLQ_NAMES.get(dlqKey);
        if (dlqName == null) {
            redirectAttributes.addFlashAttribute("errorMessage","無效的 dlqKey");
            return "redirect:/admin/dead-letters";
        }
        boolean discarded = deadLetterQueueService.discardNext(dlqName);
        redirectAttributes.addFlashAttribute(discarded ? "infoMessage" : "errorMessage",
                discarded ? "已丟棄一筆訊息" : "佇列目前沒有訊息");
        return "redirect:/admin/dead-letters";
    }
}
