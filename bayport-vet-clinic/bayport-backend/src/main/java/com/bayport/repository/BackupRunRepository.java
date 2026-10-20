package com.bayport.repository;

import com.bayport.entity.BackupRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackupRunRepository extends JpaRepository<BackupRun, Long> {
    List<BackupRun> findTop20ByOrderByStartedAtDesc();

    BackupRun findFirstByStatusOrderByFinishedAtDesc(String status);
}
