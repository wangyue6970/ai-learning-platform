package com.wangyue.backend.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OcrService {

    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);

    private final String pythonCommand;
    private final Path serverScriptPath;
    private final URI healthUri;
    private final URI recognizeUri;
    private final Duration timeout;
    private final Duration startupTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Object serverLock = new Object();
    private Process ocrServerProcess;

    public OcrService(
        @Value("${app.ocr.python-command:python}") String pythonCommand,
        @Value("${app.ocr.server-script-path:../ocr-service/ocr_server.py}") String serverScriptPath,
        @Value("${app.ocr.service-port:8765}") int servicePort,
        @Value("${app.ocr.timeout-seconds:120}") long timeoutSeconds,
        @Value("${app.ocr.startup-timeout-seconds:90}") long startupTimeoutSeconds,
        ObjectMapper objectMapper
    ) {
        this.pythonCommand = pythonCommand;
        this.serverScriptPath = Path.of(serverScriptPath).toAbsolutePath().normalize();
        this.healthUri = URI.create("http://127.0.0.1:" + servicePort + "/health");
        this.recognizeUri = URI.create("http://127.0.0.1:" + servicePort + "/recognize");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.startupTimeout = Duration.ofSeconds(startupTimeoutSeconds);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT)
            .build();
    }

    public String recognizeImage(Path imagePath) {
        Path normalizedImagePath = imagePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedImagePath)) {
            throw new IllegalArgumentException("待识别图片不存在");
        }

        ensureOcrServerStarted();

        try {
            HttpRequest request = HttpRequest.newBuilder(recognizeUri)
                .timeout(timeout)
                .header("Content-Type", "application/octet-stream")
                .header("X-Image-Suffix", getImageSuffix(normalizedImagePath))
                .POST(HttpRequest.BodyPublishers.ofFile(normalizedImagePath))
                .build();
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException("图片识别失败：" + readErrorMessage(response.body()));
            }

            String text = objectMapper.readTree(response.body()).path("text").asText().trim();
            if (text.isBlank()) {
                throw new IllegalStateException("图片未识别出可用文字");
            }
            return text;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片识别被中断，请稍后重试", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接本地 OCR 服务，请检查后端是否正在运行", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("读取 OCR 识别结果失败", exception);
        }
    }

    private void ensureOcrServerStarted() {
        synchronized (serverLock) {
            if (isOcrServerHealthy()) {
                return;
            }
            if (!Files.isRegularFile(serverScriptPath)) {
                throw new IllegalStateException("OCR 服务脚本不存在，请检查本地 OCR 配置");
            }

            stopOcrServer();
            try {
                ocrServerProcess = new ProcessBuilder(
                    pythonCommand,
                    serverScriptPath.toString(),
                    String.valueOf(recognizeUri.getPort())
                )
                    .inheritIO()
                    .start();
                waitForOcrServer();
            } catch (IOException exception) {
                throw new IllegalStateException("无法启动本地 OCR 服务，请检查 Python 配置", exception);
            }
        }
    }

    private void waitForOcrServer() {
        long deadline = System.nanoTime() + startupTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isOcrServerHealthy()) {
                return;
            }
            if (ocrServerProcess == null || !ocrServerProcess.isAlive()) {
                throw new IllegalStateException("本地 OCR 服务启动失败，请查看后端终端中的 Python 错误");
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待本地 OCR 服务启动时被中断", exception);
            }
        }

        stopOcrServer();
        throw new IllegalStateException("本地 OCR 服务启动超时，请稍后重试");
    }

    private boolean isOcrServerHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .GET()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String getImageSuffix(Path imagePath) {
        String fileName = imagePath.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex >= 0 ? fileName.substring(extensionIndex) : ".jpg";
    }

    private String readErrorMessage(String responseBody) {
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            String message = body.path("error").asText().trim();
            return message.isBlank() ? "OCR 服务没有返回可读错误信息" : message;
        } catch (JacksonException exception) {
            return "OCR 服务返回了无法读取的错误信息";
        }
    }

    @PreDestroy
    void stopOcrServer() {
        if (ocrServerProcess == null || !ocrServerProcess.isAlive()) {
            return;
        }
        ocrServerProcess.destroy();
        try {
            if (!ocrServerProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                ocrServerProcess.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ocrServerProcess.destroyForcibly();
        }
    }
}
