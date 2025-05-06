package com.recapgrid.repository;

import com.recapgrid.model.StatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusRepository extends JpaRepository<StatusEntity, String> {
    StatusEntity findByUserId(String userId);
}
