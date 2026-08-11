package com.wangyue.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic", "docx");

    private final Path storageDirectory;

    public ImportStorageService(@Value("${app.import.storage-dir}") String storageDir) {
        this.storageDirectory = Paths.get(storageDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建导入文件保存目录", exception);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        String extension = getExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、HEIC 图片和 DOCX 文件");
        }

        Path targetPath = storageDirectory.resolve(UUID.randomUUID() + "." + extension).normalize();
        if (!targetPath.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("非法文件路径");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("保存上传文件失败", exception);
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
