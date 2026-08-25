package com.wangyue.backend.service;

import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.mapper.ImportFileMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Cleans files left behind by an earlier interrupted delete or an old version
 * of the app. Import rows still in MySQL are always treated as active and are
 * therefore preserved.
 */
@Service
public class ImportStorageCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ImportStorageCleanupService.class);

    private final ImportFileMapper importFileMapper;
    private final ImportStorageService importStorageService;

    public ImportStorageCleanupService(
        ImportFileMapper importFileMapper,
        ImportStorageService importStorageService
    ) {
        this.importFileMapper = importFileMapper;
        this.importStorageService = importStorageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanHistoricalOrphanedFiles() {
        List<String> referencedStoredFilePaths = importFileMapper.selectList(null)
            .stream()
            .map(ImportFile::getStoredFilePath)
            .toList();

        int deletedCount = importStorageService.deleteUnreferencedStoredFiles(referencedStoredFilePaths);
        if (deletedCount > 0) {
            logger.info("Cleaned up {} historical orphaned import file(s)", deletedCount);
        }
    }
}
