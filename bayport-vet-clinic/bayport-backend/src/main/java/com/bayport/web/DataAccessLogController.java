package com.bayport.web;

import com.bayport.entity.DataAccessLog;
import com.bayport.repository.DataAccessLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/data-access-logs")
@PreAuthorize("hasRole('ADMIN')")
public class DataAccessLogController {

    private final DataAccessLogRepository dataAccessLogRepository;

    public DataAccessLogController(DataAccessLogRepository dataAccessLogRepository) {
        this.dataAccessLogRepository = dataAccessLogRepository;
    }

    @GetMapping
    public Page<DataAccessLog> getDataAccessLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return dataAccessLogRepository.findAll(pageable);
    }
}
