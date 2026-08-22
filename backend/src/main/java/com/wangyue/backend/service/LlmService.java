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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Only communicates with DeepSeek and converts its JSON response into Java
 * objects. Validation and database writes stay in QuestionDraftService.
 */
@Service
public class LlmService {

    private static final Pattern OPTION_TEXT_PATTERN = Pattern.compile(
        "^\\s*([A-Za-z])\\s*(?:[.、．:：)）]|\\s)\\s*(.+?)\\s*$"
    );

    private final HttpClient httpClient;
    private final URI generateUri;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxOutputTokens;
    private final ObjectMapper objectMapper;

    public LlmService(
        @Value("${app.llm.base-url:https://api.deepseek.com}") String baseUrl,
        @Value("${app.llm.api-key:}") String apiKey,
        @Value("${app.llm.model:deepseek-v4-flash}") String model,
        @Value("${app.llm.timeout-seconds:180}") long timeoutSeconds,
        @Value("${app.llm.max-output-tokens:4096}") int maxOutputTokens,
        ObjectMapper objectMapper
    ) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.generateUri = URI.create(baseUrl + "/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxOutputTokens = maxOutputTokens;
        this.objectMapper = objectMapper;
    }

    public List<RecognizedQuestion> structureQuestions(String recognitionText) {
        if (recognitionText == null || recognitionText.isBlank()) {
            throw new IllegalArgumentException("没有可生成题目的识别文字");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("没有配置 DeepSeek API Key，请在后端电脑设置 DEEPSEEK_API_KEY 后重启后端");
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0);
            requestBody.put("max_tokens", maxOutputTokens);
            requestBody.putObject("thinking").put("type", "disabled");
            requestBody.putObject("response_format").put("type", "json_object");
            var messages = requestBody.putArray("messages");
            messages.addObject()
                .put("role", "system")
                .put("content", buildSystemPrompt());
            messages.addObject()
                .put("role", "user")
                .put("content", recognitionText);

            HttpRequest request = HttpRequest.newBuilder(generateUri)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw createDeepSeekError(response.statusCode());
            }

            JsonNode outerResponse = objectMapper.readTree(response.body());
            String finishReason = outerResponse.path("choices").path(0).path("finish_reason").asText();
            if ("length".equals(finishReason)) {
                throw new IllegalStateException("DeepSeek 本批题目输出过长而被截断，请稍后重试");
            }
            String modelOutput = outerResponse.path("choices").path(0).path("message").path("content").asText().trim();
            if (modelOutput.isBlank()) {
                throw new IllegalStateException("DeepSeek 没有返回题目内容");
            }

            JsonNode result = objectMapper.readTree(extractJsonObject(modelOutput));
            JsonNode questions = result.path("questions");
            if (!questions.isArray() || questions.isEmpty()) {
                throw new IllegalStateException("DeepSeek 没有返回可用题目");
            }
            normalizeQuestionStem(questions);
            normalizeStringOptions(questions);
            return objectMapper.convertValue(questions, new TypeReference<List<RecognizedQuestion>>() {});
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new IllegalStateException("DeepSeek 生成题目超时，请稍后重试", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接 DeepSeek，请检查电脑网络后重试", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DeepSeek 生成被中断，请稍后重试", exception);
        } catch (JacksonException exception) {
            throw new IllegalStateException("DeepSeek 返回的题目格式错误，请稍后重试", exception);
        }
    }

    private IllegalStateException createDeepSeekError(int statusCode) {
        if (statusCode == 401) {
            return new IllegalStateException("DeepSeek API Key 无效或未配置，请检查后端电脑的 DEEPSEEK_API_KEY");
        }
        if (statusCode == 402) {
            return new IllegalStateException("DeepSeek API 余额不足，请充值后重试");
        }
        if (statusCode == 429) {
            return new IllegalStateException("DeepSeek 请求过于频繁，请稍后重试");
        }
        return new IllegalStateException("DeepSeek 服务返回错误：HTTP " + statusCode);
    }

    private String buildSystemPrompt() {
        return """
            你是题目整理助手。请从下面 OCR 文字中尽量逐题提取考试题目。

            只返回一个 JSON 对象，不能添加 Markdown、解释或任何额外文字。

            规则：
            1. questionType 只能是 SINGLE_CHOICE、MULTIPLE_CHOICE、TRUE_FALSE。
            2. 每道题必须提供 stem 字段，值是题号之后、A 选项之前的完整原文题干。
               不得省略题干，不得使用 question、title、questionText 等其他字段名。
            3. 单选、多选题的 options 必须是对象数组。每一项必须严格写成
               {"optionKey":"A","content":"选项文字"}，不能写成 "A. 选项文字" 这样的字符串。
               判断题 options 为空数组。
            4. 只有原文明确给出答案时才能填写 correctAnswer；没有答案时返回空数组，不能猜答案。
            5. 原文没有解析时 explanation 必须为 null；原文没有知识点时 knowledgePoints 必须为空数组。
            6. 无法可靠判断为题目的片段不要输出。
            7. 保留原题文字，不要改写题干和选项，也不要生成原文没有的信息。
            返回格式必须是一个 JSON 对象，例如：
            {"questions":[{"questionType":"SINGLE_CHOICE","stem":"完整题干","options":[{"optionKey":"A","content":"选项文字"}],"correctAnswer":["A"],"explanation":null,"knowledgePoints":[]}]}
        """;
    }

    /**
     * The API contract calls the question body "stem". A model may sometimes
     * return a readable alias despite the prompt, so copy known aliases before
     * the DTO mapping rather than turning a recoverable response into an empty
     * stem draft. A truly missing stem remains visible for user repair.
     */
    private void normalizeQuestionStem(JsonNode questions) {
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            JsonNode question = questions.get(questionIndex);
            if (!(question instanceof ObjectNode questionObject)) {
                throw new IllegalStateException("DeepSeek 返回的第 " + (questionIndex + 1) + " 道题不是对象");
            }
            if (hasText(questionObject.path("stem"))) {
                continue;
            }
            for (String alias : List.of("question", "questionStem", "questionText", "title")) {
                JsonNode value = questionObject.path(alias);
                if (hasText(value)) {
                    questionObject.put("stem", value.asText().trim());
                    break;
                }
            }
        }
    }

    private boolean hasText(JsonNode value) {
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    /**
     * DeepSeek normally follows the requested option-object schema, but it can
     * occasionally return a readable string such as "A. 管理文件". Convert that
     * harmless variant before mapping JSON to Java DTOs; an unmarked option is
     * still rejected so it can never silently become a wrong draft.
     */
    private void normalizeStringOptions(JsonNode questions) {
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            JsonNode question = questions.get(questionIndex);
            if (!(question instanceof ObjectNode questionObject)) {
                throw new IllegalStateException("DeepSeek 返回的第 " + (questionIndex + 1) + " 道题不是对象");
            }

            JsonNode options = questionObject.path("options");
            if (options.isMissingNode() || options.isNull()) {
                continue;
            }
            if (!options.isArray()) {
                throw new IllegalStateException("DeepSeek 返回的第 " + (questionIndex + 1) + " 道题选项不是数组");
            }

            ArrayNode normalizedOptions = objectMapper.createArrayNode();
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                JsonNode option = options.get(optionIndex);
                if (option.isObject()) {
                    normalizedOptions.add(option);
                    continue;
                }
                if (!option.isTextual()) {
                    throw invalidOption(questionIndex, optionIndex);
                }

                Matcher matcher = OPTION_TEXT_PATTERN.matcher(option.asText());
                if (!matcher.matches()) {
                    throw invalidOption(questionIndex, optionIndex);
                }
                normalizedOptions.addObject()
                    .put("optionKey", matcher.group(1).toUpperCase())
                    .put("content", matcher.group(2));
            }
            questionObject.set("options", normalizedOptions);
        }
    }

    private IllegalStateException invalidOption(int questionIndex, int optionIndex) {
        return new IllegalStateException(
            "DeepSeek 返回的第 " + (questionIndex + 1) + " 道题第 " + (optionIndex + 1)
                + " 个选项缺少 A/B/C 标记，请稍后重试"
        );
    }

    /**
     * JSON mode should return an object only, but a provider can still wrap it
     * in a Markdown code fence. Remove that harmless wrapper before parsing;
     * malformed JSON is still rejected by ObjectMapper and never reaches drafts.
     */
    private String extractJsonObject(String modelOutput) {
        String candidate = modelOutput.trim();
        if (candidate.startsWith("```")) {
            int firstLineBreak = candidate.indexOf('\n');
            int closingFence = candidate.lastIndexOf("```");
            if (firstLineBreak >= 0 && closingFence > firstLineBreak) {
                candidate = candidate.substring(firstLineBreak + 1, closingFence).trim();
            }
        }

        int objectStart = candidate.indexOf('{');
        int objectEnd = candidate.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return candidate.substring(objectStart, objectEnd + 1);
        }
        return candidate;
    }
}
