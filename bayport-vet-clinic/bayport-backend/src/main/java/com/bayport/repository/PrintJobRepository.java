package com.bayport.repository;

import com.bayport.entity.PrintJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {
    List<PrintJob> findTop20ByBranchCodeAndStatusOrderByCreatedAtAsc(String branchCode, String status);
}
