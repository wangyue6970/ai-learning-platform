package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.mapper.ImportFileMapper;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

/**
 * A small local-development queue. OCR and the local LLM share one worker so
 * a large import batch does not start dozens of heavy Python/model jobs at
 * once. Per-file state remains in MySQL for the client to poll.
 */
@Service
public class ImportProcessingQueue {

    private final ImportFileMapper importFileMapper;
    private final ImportService importService;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "import-processing-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Long> queuedFileIds = ConcurrentHashMap.newKeySet();

    public ImportProcessingQueue(ImportFileMapper importFileMapper, ImportService importService) {
        this.importFileMapper = importFileMapper;
        this.importService = importService;
    }

    public void enqueueBatch(Long importBatchId) {
        importFileMapper.selectList(new LambdaQueryWrapper<ImportFile>()
            .eq(ImportFile::getImportBatchId, importBatchId)
            .in(ImportFile::getStatus, "WAITING_RECOGNITION", "WAITING_STRUCTURING")
            .orderByAsc(ImportFile::getId)
        ).forEach(file -> enqueueFile(file.getId()));
    }

    private void enqueueFile(Long importFileId) {
        if (!queuedFileIds.add(importFileId)) {
            return;
        }

        worker.execute(() -> {
            try {
                importService.processFileInBackground(importFileId);
            } finally {
                queuedFileIds.remove(importFileId);
            }
        });
    }

    @PreDestroy
    void closeWorker() {
        worker.shutdownNow();
    }
}
