package com.recapgrid.repository;

import com.recapgrid.model.Processed;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProcessedRepository extends JpaRepository<Processed, Long> {
    List<Processed> findByUserId(String userId);
}
