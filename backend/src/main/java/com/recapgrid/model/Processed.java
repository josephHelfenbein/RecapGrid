package com.recapgrid.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "processed")
public class Processed {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String fileName;
    private String fileUrl;
    private OffsetDateTime uploadedAt;

    public Processed() {}

    public Processed(String userId, String fileName, String fileUrl) {
        this.userId = userId;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.uploadedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getFileName() { return fileName; }
    public String getFileUrl() { return fileUrl; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
