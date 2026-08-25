package com.wangyue.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void removesOnlyUnreferencedFilesInsideItsOwnDirectory() throws Exception {
        Path uploadDirectory = temporaryDirectory.resolve("uploads");
        Path referencedFile = uploadDirectory.resolve("keep.docx");
        Path orphanedFile = uploadDirectory.resolve("delete.docx");
        Path externalFile = temporaryDirectory.resolve("must-stay.docx");
        Files.createDirectories(uploadDirectory);
        Files.writeString(referencedFile, "keep");
        Files.writeString(orphanedFile, "delete");
        Files.writeString(externalFile, "must stay");

        ImportStorageService storageService = new ImportStorageService(uploadDirectory.toString());

        int deletedCount = storageService.deleteUnreferencedStoredFiles(
            List.of(referencedFile.toString(), externalFile.toString())
        );

        assertEquals(1, deletedCount);
        assertTrue(Files.exists(referencedFile));
        assertFalse(Files.exists(orphanedFile));
        assertTrue(Files.exists(externalFile));
    }

    @Test
    void ignoresInvalidOrExternalPathsDuringBestEffortCleanup() throws Exception {
        Path uploadDirectory = temporaryDirectory.resolve("uploads");
        Path managedFile = uploadDirectory.resolve("managed.docx");
        Path externalFile = temporaryDirectory.resolve("must-stay.docx");
        Files.createDirectories(uploadDirectory);
        Files.writeString(managedFile, "managed");
        Files.writeString(externalFile, "must stay");

        ImportStorageService storageService = new ImportStorageService(uploadDirectory.toString());

        int deletedCount = storageService.deleteManagedFiles(
            List.of(managedFile.toString(), externalFile.toString())
        );

        assertEquals(1, deletedCount);
        assertFalse(Files.exists(managedFile));
        assertTrue(Files.exists(externalFile));
    }
}
