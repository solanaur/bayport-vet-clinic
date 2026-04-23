package com.bayport.repository;

import com.bayport.entity.DataAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataAccessLogRepository extends JpaRepository<DataAccessLog, Long> {
}
