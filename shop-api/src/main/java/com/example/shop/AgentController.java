package com.example.shop;

import com.example.shop.Models.AgentRequest;
import com.example.shop.Models.AgentResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService service;

    @Value("${agent.token:}")
    private String token;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestHeader(value = "X-Agent-Token", required = false) String header,
                              @Valid @RequestBody AgentRequest body) {
        // This endpoint spends money per call, so protect it when AGENT_TOKEN is set.
        if (token != null && !token.isBlank()) {
            byte[] expected = token.getBytes(StandardCharsets.UTF_8);
            byte[] given = header == null ? new byte[0] : header.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, given)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid X-Agent-Token");
            }
        }
        return service.chat(body.question());
    }
}
