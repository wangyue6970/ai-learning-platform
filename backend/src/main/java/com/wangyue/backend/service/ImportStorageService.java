package com.wangyue.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic", "docx");
    private static final Logger logger = LoggerFactory.getLogger(ImportStorageService.class);

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

    /** Deletes only a file that was stored in this service's private directory. */
    public void deleteStoredFile(String storedFilePath) {
        if (storedFilePath == null || storedFilePath.isBlank()) {
            return;
        }

        Path targetPath = Paths.get(storedFilePath).toAbsolutePath().normalize();
        if (!targetPath.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("非法临时文件路径");
        }

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            throw new IllegalStateException("临时原文件删除失败", exception);
        }
    }

    /**
     * Best-effort cleanup for files whose database row has already been deleted.
     * It intentionally skips invalid or external paths so cleanup can never
     * remove a file outside this service's private upload directory.
     */
    public int deleteManagedFiles(Iterable<String> storedFilePaths) {
        if (storedFilePaths == null) {
            return 0;
        }

        int deletedCount = 0;
        for (String storedFilePath : storedFilePaths) {
            Path managedPath = resolveManagedPathOrNull(storedFilePath);
            if (managedPath == null) {
                if (storedFilePath != null && !storedFilePath.isBlank()) {
                    logger.warn("Skip cleanup for a file outside the managed import directory: {}", storedFilePath);
                }
                continue;
            }

            try {
                if (Files.deleteIfExists(managedPath)) {
                    deletedCount++;
                }
            } catch (IOException exception) {
                // The database row may already be gone. Keep the delete action
                // successful and let the next startup sweep retry this orphan.
                logger.warn("Failed to clean up managed import file {}", managedPath, exception);
            }
        }
        return deletedCount;
    }

    /**
     * Deletes only regular files in the private upload directory which are no
     * longer referenced by any import_file row. This is used after startup to
     * clean historical leftovers without touching active imports.
     */
    public int deleteUnreferencedStoredFiles(Collection<String> referencedStoredFilePaths) {
        Set<Path> referencedPaths = new HashSet<>();
        if (referencedStoredFilePaths != null) {
            for (String storedFilePath : referencedStoredFilePaths) {
                Path managedPath = resolveManagedPathOrNull(storedFilePath);
                if (managedPath != null) {
                    referencedPaths.add(managedPath);
                }
            }
        }

        try (Stream<Path> storedFiles = Files.list(storageDirectory)) {
            return storedFiles
                .filter(Files::isRegularFile)
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> !referencedPaths.contains(path))
                .mapToInt(path -> deleteManagedFiles(Set.of(path.toString())))
                .sum();
        } catch (IOException exception) {
            logger.warn("Failed to scan the managed import directory for orphan files", exception);
            return 0;
        }
    }

    private Path resolveManagedPathOrNull(String storedFilePath) {
        if (storedFilePath == null || storedFilePath.isBlank()) {
            return null;
        }

        try {
            Path targetPath = Paths.get(storedFilePath).toAbsolutePath().normalize();
            return targetPath.startsWith(storageDirectory) ? targetPath : null;
        } catch (RuntimeException exception) {
            logger.warn("Skip cleanup for an invalid stored file path: {}", storedFilePath);
            return null;
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
