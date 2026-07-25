package com.sprint.mission.otboo.global.entity;

import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.Getter;

@Getter
@Embeddable
public class SoftDeletable {

    private Instant deletedAt;

    public void delete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}