package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.mapper.ImportFileMapper;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private final Map<Long, FutureTask<Void>> queuedTasks = new ConcurrentHashMap<>();

    @Value("${app.import.processing.enabled:true}")
    private boolean backgroundProcessingEnabled;

    public ImportProcessingQueue(ImportFileMapper importFileMapper, ImportService importService) {
        this.importFileMapper = importFileMapper;
        this.importService = importService;
    }

    /**
     * The queue itself lives only in memory. If the local server is restarted,
     * MySQL still remembers files that were uploaded but not yet processed.
     * Put those files back into the queue after Spring Boot is ready so a user
     * never has to upload the same Word document or image again.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingFilesAfterStartup() {
        if (!backgroundProcessingEnabled) {
            return;
        }

        importFileMapper.selectList(new LambdaQueryWrapper<ImportFile>()
            .in(
                ImportFile::getStatus,
                "WAITING_RECOGNITION", "WAITING_STRUCTURING", "RECOGNIZING", "STRUCTURING"
            )
            .orderByAsc(ImportFile::getId)
        ).forEach(file -> {
            // RECOGNIZING / STRUCTURING means the previous server stopped in
            // the middle of a task. Convert it to a runnable state first.
            if ("RECOGNIZING".equals(file.getStatus())) {
                file.setStatus("WAITING_RECOGNITION");
                importFileMapper.updateById(file);
            } else if ("STRUCTURING".equals(file.getStatus())) {
                file.setStatus(file.getRecognitionText() == null || file.getRecognitionText().isBlank()
                    ? "WAITING_RECOGNITION" : "WAITING_STRUCTURING");
                importFileMapper.updateById(file);
            }
            enqueueFile(file.getId());
        });
    }

    public void enqueueBatch(Long importBatchId) {
        if (!backgroundProcessingEnabled) {
            return;
        }

        importFileMapper.selectList(new LambdaQueryWrapper<ImportFile>()
            .eq(ImportFile::getImportBatchId, importBatchId)
            .in(ImportFile::getStatus, "WAITING_RECOGNITION", "WAITING_STRUCTURING")
            .orderByAsc(ImportFile::getId)
        ).forEach(file -> enqueueFile(file.getId()));
    }

    /** Adds one already-uploaded file back to the single background worker. */
    public void enqueueFile(Long importFileId) {
        if (!queuedFileIds.add(importFileId)) {
            return;
        }

        FutureTask<Void> task = new FutureTask<>(() -> {
            processQueuedFile(importFileId);
            return null;
        });
        queuedTasks.put(importFileId, task);
        worker.execute(task);
    }

    /**
     * A new Word replaces unfinished Word imports in the same library. Cancel
     * their queued or running work before the service deletes their temporary
     * files and database rows.
     */
    public void cancelFiles(Iterable<Long> importFileIds) {
        if (importFileIds == null) {
            return;
        }
        for (Long importFileId : importFileIds) {
            if (importFileId == null) {
                continue;
            }
            FutureTask<Void> task = queuedTasks.remove(importFileId);
            if (task != null) {
                task.cancel(true);
            }
            queuedFileIds.remove(importFileId);
        }
    }

    private void processQueuedFile(Long importFileId) {
        try {
            importService.processFileInBackground(importFileId);
        } finally {
            queuedFileIds.remove(importFileId);
            queuedTasks.remove(importFileId);
        }
    }

    @PreDestroy
    void closeWorker() {
        queuedTasks.values().forEach(task -> task.cancel(true));
        worker.shutdownNow();
    }
}
