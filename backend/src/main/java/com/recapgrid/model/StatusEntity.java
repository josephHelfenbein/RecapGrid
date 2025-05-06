package com.recapgrid.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.OffsetDateTime;

@Entity
@Table(name = "status")
public class StatusEntity {
    @Id
    private String id;
    
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "stage")
    private String stage;

    @Column(name = "info")
    private String info;

    public StatusEntity() {}

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStage() {
        return stage;
    }
    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getInfo() {
        return info;
    }
    public void setInfo(String info) {
        this.info = info;
    }
}
