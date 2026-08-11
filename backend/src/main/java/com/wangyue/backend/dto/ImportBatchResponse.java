package com.wangyue.backend.dto;

import java.util.List;

public class ImportBatchResponse {

    private Long id;
    private Long libraryId;
    private String status;
    private List<ImportFileResponse> files;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLibraryId() { return libraryId; }
    public void setLibraryId(Long libraryId) { this.libraryId = libraryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ImportFileResponse> getFiles() { return files; }
    public void setFiles(List<ImportFileResponse> files) { this.files = files; }
}
