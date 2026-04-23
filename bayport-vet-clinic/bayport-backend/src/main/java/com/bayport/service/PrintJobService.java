package com.bayport.service;

import com.bayport.entity.PrintJob;
import com.bayport.repository.PrintJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PrintJobService {
    private final PrintJobRepository printJobRepository;

    public PrintJobService(PrintJobRepository printJobRepository) {
        this.printJobRepository = printJobRepository;
    }

    public PrintJob enqueue(String branchCode, String deviceName, String html) {
        if (branchCode == null || branchCode.isBlank()) {
            throw new IllegalArgumentException("branchCode is required");
        }
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("receiptHtml is required");
        }
        PrintJob job = new PrintJob();
        job.setBranchCode(branchCode.trim());
        job.setDeviceName(deviceName != null ? deviceName.trim() : null);
        job.setReceiptHtml(html);
        job.setStatus(PrintJob.STATUS_QUEUED);
        return printJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public List<PrintJob> pollQueued(String branchCode) {
        return printJobRepository.findTop20ByBranchCodeAndStatusOrderByCreatedAtAsc(branchCode, PrintJob.STATUS_QUEUED);
    }

    public PrintJob markProcessing(Long id) {
        PrintJob job = get(id);
        job.setStatus(PrintJob.STATUS_PROCESSING);
        job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
        return printJobRepository.save(job);
    }

    public PrintJob markPrinted(Long id) {
        PrintJob job = get(id);
        job.setStatus(PrintJob.STATUS_PRINTED);
        job.setLastError(null);
        return printJobRepository.save(job);
    }

    public PrintJob markFailed(Long id, String error) {
        PrintJob job = get(id);
        job.setStatus(PrintJob.STATUS_FAILED);
        job.setLastError(error);
        return printJobRepository.save(job);
    }

    private PrintJob get(Long id) {
        return printJobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Print job not found"));
    }
}
