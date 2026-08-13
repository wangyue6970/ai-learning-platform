package com.wangyue.backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OcrService {

    private static final String RESULT_PREFIX = "__OCR_RESULT__";

    private final String pythonCommand;
    private final Path scriptPath;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public OcrService(
        @Value("${app.ocr.python-command:python}") String pythonCommand,
        @Value("${app.ocr.script-path:../ocr-service/ocr_image.py}") String scriptPath,
        @Value("${app.ocr.timeout-seconds:120}") long timeoutSeconds,
        ObjectMapper objectMapper
    ) {
        this.pythonCommand = pythonCommand;
        this.scriptPath = Path.of(scriptPath).toAbsolutePath().normalize();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.objectMapper = objectMapper;
    }

    public String recognizeImage(Path imagePath) {
        Path normalizedImagePath = imagePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedImagePath)) {
            throw new IllegalArgumentException("待识别图片不存在");
        }
        if (!Files.isRegularFile(scriptPath)) {
            throw new IllegalStateException("OCR 脚本不存在，请检查本地 OCR 配置");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
            pythonCommand,
            scriptPath.toString(),
            normalizedImagePath.toString()
        );
        processBuilder.redirectErrorStream(true);

        ExecutorService outputReader = Executors.newSingleThreadExecutor();
        try {
            Process process = processBuilder.start();
            Future<String> outputFuture = outputReader.submit(() -> readUtf8(process.getInputStream()));
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException("图片识别超时，请稍后重试");
            }

            String output = outputFuture.get(10, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("图片识别失败：" + readableError(output));
            }
            return extractText(output);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动本地 OCR 服务，请检查 Python 配置", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片识别被中断，请稍后重试", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("读取 OCR 识别结果失败", exception);
        } finally {
            outputReader.shutdownNow();
        }
    }

    private String readUtf8(InputStream inputStream) throws IOException {
        try (inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    private String extractText(String output) {
        String resultJson = Arrays.stream(output.split("\\R"))
            .filter(line -> line.startsWith(RESULT_PREFIX))
            .reduce((first, second) -> second)
            .map(line -> line.substring(RESULT_PREFIX.length()))
            .orElseThrow(() -> new IllegalStateException("图片未识别出可用文字"));

        try {
            JsonNode result = objectMapper.readTree(resultJson);
            String text = result.path("text").asText().trim();
            if (text.isBlank()) {
                throw new IllegalStateException("图片未识别出可用文字");
            }
            return text;
        } catch (JacksonException exception) {
            throw new IllegalStateException("OCR 返回结果格式错误", exception);
        }
    }

    private String readableError(String output) {
        String trimmedOutput = output.trim();
        return trimmedOutput.isBlank() ? "OCR 程序没有返回错误信息" : trimmedOutput;
    }
}
