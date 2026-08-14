package com.wangyue.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
public class WordDocumentService {

    public String extractText(Path documentPath) {
        Path normalizedDocumentPath = documentPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedDocumentPath)) {
            throw new IllegalArgumentException("待解析的 Word 文件不存在");
        }

        try (
            InputStream inputStream = Files.newInputStream(normalizedDocumentPath);
            XWPFDocument document = new XWPFDocument(inputStream)
        ) {
            String text = document.getParagraphs().stream()
                .map(paragraph -> paragraph.getText().trim())
                .filter(paragraphText -> !paragraphText.isBlank())
                .collect(Collectors.joining(System.lineSeparator()));

            if (text.isBlank()) {
                throw new IllegalStateException("Word 文件中没有读取到可用文字");
            }
            return text;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Word 文件解析失败，请确认文件未损坏且格式为 .docx", exception);
        }
    }
}
