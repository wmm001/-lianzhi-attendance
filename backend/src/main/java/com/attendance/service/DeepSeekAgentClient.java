package com.attendance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Answers general questions; attendance facts are supplied only after role-scoped database lookup. */
@Service
public class DeepSeekAgentClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekAgentClient.class);
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${DEEPSEEK_API_KEY:}") private String apiKey;
    @Value("${deepseek.base-url:https://api.deepseek.com}") private String baseUrl;
    @Value("${deepseek.model:deepseek-flash}") private String model;

    public DeepSeekAgentClient(ObjectMapper json) { this.json = json; }

    public Optional<String> explain(String question, String groundedAnswer) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        try {
            String prompt = groundedAnswer == null || groundedAnswer.isBlank() ? question :
                    "用户问题：" + question + "\n本系统已核实的事实：" + groundedAnswer +
                    "\n涉及本系统的具体数据时请以这些事实为准；其他知识可以正常回答。不要编造未提供的签到记录或操作结果。";
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "stream", false,
                    "thinking", Map.of("type", "disabled"),
                    "temperature", 0.2,
                    "max_tokens", 180,
                    "messages", List.of(
                            Map.of("role", "system", "content", "你可以回答任何主题的问题。涉及本系统的员工、考勤和场所等具体数据时，仅依据提供的已核实事实；若无数据，说明无法核实。不能查询或泄露其他账号的私有数据。回答简洁。"),
                            Map.of("role", "user", "content", prompt))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("DeepSeek request failed with HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode choices = json.readTree(response.body()).path("choices");
            String answer = choices.isArray() && !choices.isEmpty() ?
                    choices.get(0).path("message").path("content").asText("").trim() : "";
            return answer.isBlank() ? Optional.empty() : Optional.of(answer);
        } catch (Exception error) {
            log.warn("DeepSeek request unavailable: {}", error.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
