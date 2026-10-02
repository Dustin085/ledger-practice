package com.example.ledgerpractice.admin;

import com.example.ledgerpractice.outbox.DeadLetterQueueService;
import com.example.ledgerpractice.outbox.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeadLetterController.class)
class DeadLetterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeadLetterQueueService deadLetterQueueService;

    @Test
    void reprocessWithUnknownKeyFlashesErrorAndDoesNotTouchQueue() throws Exception {
        mockMvc.perform(post("/admin/dead-letters/bogus/reprocess"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dead-letters"))
                .andExpect(flash().attribute("errorMessage", "無效的 dlqKey"));

        verifyNoInteractions(deadLetterQueueService);
    }

    @Test
    void discardWithUnknownKeyFlashesErrorAndDoesNotTouchQueue() throws Exception {
        mockMvc.perform(post("/admin/dead-letters/bogus/discard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dead-letters"))
                .andExpect(flash().attribute("errorMessage", "無效的 dlqKey"));

        verifyNoInteractions(deadLetterQueueService);
    }

    @Test
    void reprocessWithValidKeyAndMessageFlashesInfo() throws Exception {
        when(deadLetterQueueService.reprocessNext(RabbitConfig.SUBMISSION_DEAD_LETTER_QUEUE_NAME)).thenReturn(true);

        mockMvc.perform(post("/admin/dead-letters/submission/reprocess"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dead-letters"))
                .andExpect(flash().attribute("infoMessage", "已重新送出一筆訊息"))
                .andExpect(flash().attributeCount(1));

        verify(deadLetterQueueService).reprocessNext(RabbitConfig.SUBMISSION_DEAD_LETTER_QUEUE_NAME);
    }

    @Test
    void reprocessWithValidKeyButEmptyQueueFlashesError() throws Exception {
        when(deadLetterQueueService.reprocessNext(RabbitConfig.TRANSFER_RESULT_DEAD_LETTER_QUEUE_NAME)).thenReturn(false);

        mockMvc.perform(post("/admin/dead-letters/inbox/reprocess"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dead-letters"))
                .andExpect(flash().attribute("errorMessage", "佇列目前沒有訊息"))
                .andExpect(flash().attributeCount(1));

        verify(deadLetterQueueService, never()).discardNext(RabbitConfig.TRANSFER_RESULT_DEAD_LETTER_QUEUE_NAME);
    }
}
