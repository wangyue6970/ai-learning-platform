package com.wangyue.backend.service;

import com.wangyue.backend.dto.RecognizedQuestion;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Only communicates with the local LLM and converts its JSON response into
 * Java objects. Validation and database writes stay in QuestionDraftService.
 */
@Service
public class LlmService {

    private final HttpClient httpClient;
    private final URI generateUri;
    private final String model;
    private final Duration timeout;
    private final int maxOutputTokens;
    private final ObjectMapper objectMapper;

    public LlmService(
        @Value("${app.llm.base-url:http://127.0.0.1:11434}") String baseUrl,
        @Value("${app.llm.model:qwen3:4b}") String model,
        @Value("${app.llm.timeout-seconds:120}") long timeoutSeconds,
        @Value("${app.llm.max-output-tokens:4096}") int maxOutputTokens,
        ObjectMapper objectMapper
    ) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.generateUri = URI.create(baseUrl + "/api/generate");
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxOutputTokens = maxOutputTokens;
        this.objectMapper = objectMapper;
    }

    public List<RecognizedQuestion> structureQuestions(String recognitionText) {
        if (recognitionText == null || recognitionText.isBlank()) {
            throw new IllegalArgumentException("没有可生成题目的识别文字");
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("stream", false);
            requestBody.put("think", false);
            requestBody.set("format", buildResponseSchema());
            requestBody.put("prompt", buildPrompt(recognitionText));
            requestBody.putObject("options")
                .put("num_predict", maxOutputTokens)
                .put("temperature", 0)
                .put("seed", 42);

            HttpRequest request = HttpRequest.newBuilder(generateUri)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("本地 AI 服务返回错误：HTTP " + response.statusCode());
            }

            JsonNode outerResponse = objectMapper.readTree(response.body());
            String modelOutput = outerResponse.path("response").asText().trim();
            if (modelOutput.isBlank()) {
                throw new IllegalStateException("本地 AI 没有返回题目内容");
            }

            JsonNode result = objectMapper.readTree(modelOutput);
            JsonNode questions = result.path("questions");
            if (!questions.isArray() || questions.isEmpty()) {
                throw new IllegalStateException("本地 AI 没有返回可用题目");
            }
            return objectMapper.convertValue(questions, new TypeReference<List<RecognizedQuestion>>() {});
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new IllegalStateException("本地 AI 生成题目超时，请稍后重试", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接本地 AI，请确认 Ollama 正在运行", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("本地 AI 生成被中断，请稍后重试", exception);
        } catch (JacksonException exception) {
            throw new IllegalStateException("本地 AI 返回的题目格式错误", exception);
        }
    }

    private String buildPrompt(String recognitionText) {
        return """
            你是题目整理助手。请从下面 OCR 文字中尽量逐题提取考试题目。

            只返回一个 JSON 对象，不能添加 Markdown、解释或任何额外文字。

            规则：
            1. questionType 只能是 SINGLE_CHOICE、MULTIPLE_CHOICE、TRUE_FALSE。
            2. 单选、多选题的 options 使用原始 A、B、C、D 等选项；判断题 options 为空数组。
            3. 只有原文明确给出答案时才能填写 correctAnswer；没有答案时返回空数组，不能猜答案。
            4. 原文没有解析时 explanation 必须为 null；原文没有知识点时 knowledgePoints 必须为空数组。
            5. 无法可靠判断为题目的片段不要输出。
            6. 保留原题文字，不要改写题干和选项，也不要生成原文没有的信息。

            OCR 文字如下：
            %s
            """.formatted(recognitionText);
    }

    private JsonNode buildResponseSchema() throws JacksonException {
        return objectMapper.readTree("""
            {
              "type":"object",
              "properties":{
                "questions":{
                  "type":"array",
                  "items":{
                    "type":"object",
                    "properties":{
                      "questionType":{"type":"string","enum":["SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE"]},
                      "stem":{"type":"string"},
                      "options":{
                        "type":"array",
                        "items":{
                          "type":"object",
                          "properties":{"optionKey":{"type":"string"},"content":{"type":"string"}},
                          "required":["optionKey","content"]
                        }
                      },
                      "correctAnswer":{"type":"array","items":{"type":"string"}},
                      "explanation":{"type":["string","null"]},
                      "knowledgePoints":{"type":"array","items":{"type":"string"}}
                    },
                    "required":["questionType","stem","options","correctAnswer","explanation","knowledgePoints"]
                  }
                }
              },
              "required":["questions"]
            }
            """);
    }
}
