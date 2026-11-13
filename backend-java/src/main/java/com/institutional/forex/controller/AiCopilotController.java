package com.institutional.forex.controller;

import com.quantfx.suite.engine.QuantAiCopilotService;
import com.quantfx.suite.engine.QuantAiCopilotService.AiChatRequest;
import com.quantfx.suite.engine.QuantAiCopilotService.AiChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AiCopilotController: REST proxy connecting user chat directly to the
 * Senior Quantitative AI Copilot with full dashboard real-time context.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiCopilotController {

    private final QuantAiCopilotService aiCopilotService;

    public AiCopilotController(QuantAiCopilotService aiCopilotService) {
        this.aiCopilotService = aiCopilotService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        AiChatResponse response = aiCopilotService.processChatInquiry(request);
        return ResponseEntity.ok(response);
    }
}
