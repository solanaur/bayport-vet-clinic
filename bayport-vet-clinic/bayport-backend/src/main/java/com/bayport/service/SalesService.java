package com.bayport.service;

import com.bayport.entity.BillingRecord;
import com.bayport.entity.Sale;
import com.bayport.repository.SaleRepository;
import com.bayport.util.MoneyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SalesService {

    private static final Logger log = LoggerFactory.getLogger(SalesService.class);

    private final SaleRepository saleRepository;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SalesService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    public Sale recordSale(BillingRecord billingRecord, String source, String note) {
        Sale sale = new Sale();
        sale.setAmount(billingRecord.getAmount());
        sale.setBillingId(billingRecord.getId());
        sale.setPetId(billingRecord.getPetId());
        sale.setPetName(billingRecord.getPetName());
        sale.setSource(source);
        sale.setNote(note);
        Sale saved = saleRepository.save(sale);
        broadcast(saved);
        return saved;
    }

    public Sale recordPrescriptionSale(Sale sale) {
        Sale saved = saleRepository.save(sale);
        broadcast(saved);
        return saved;
    }

    /** Point-of-sale checkout (front desk). */
    public Sale recordPosSale(Sale sale) {
        Sale saved = saleRepository.save(sale);
        broadcast(saved);
        return saved;
    }

    public List<Sale> listSales() {
        return saleRepository.findAll();
    }

    public List<SalesPoint> summary(int days) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1L);
        List<Object[]> raw = saleRepository.sumTotalsBetween(
                start.atStartOfDay(),
                today.atTime(23, 59, 59));
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (Object[] row : raw) {
            LocalDate day = toLocalDate(row[0]);
            BigDecimal sum = (BigDecimal) row[1];
            totals.put(day, MoneyUtils.normalize(sum));
        }
        List<SalesPoint> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            points.add(new SalesPoint(day, totals.getOrDefault(day, BigDecimal.ZERO)));
        }
        return points;
    }

    private LocalDate toLocalDate(Object raw) {
        if (raw instanceof LocalDate date) {
            return date;
        }
        if (raw instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(raw.toString());
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    private void broadcast(Sale sale) {
        // Never let a broken SSE connection interfere with billing / recording the sale.
        List<SseEmitter> toRemove = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("sale")
                        .data(sale));
            } catch (Exception ex) {
                if (isClientDisconnected(ex)) {
                    // Normal scenario – client closed the connection.
                    log.debug("Sales SSE client disconnected while broadcasting sale id={}: {}", sale.getId(), ex.getMessage());
                } else {
                    // Unexpected problem with this emitter only.
                    log.error("Failed to send sale update over SSE for sale id={}: {}", sale.getId(), ex.getMessage(), ex);
                }
                toRemove.add(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignore) {
                    // ignore completion errors for already closed connections
                }
            }
        }

        if (!toRemove.isEmpty()) {
            emitters.removeAll(toRemove);
        }
    }

    /**
     * Determines whether the given exception represents a normal client disconnect / aborted request.
     */
    private boolean isClientDisconnected(Throwable ex) {
        // Direct AsyncRequestNotUsableException from Spring
        if (ex instanceof AsyncRequestNotUsableException) {
            return true;
        }

        // Unwrap common servlet container exceptions via cause chain
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            // org.apache.catalina.connector.ClientAbortException is Tomcat-specific
            if (className.endsWith("ClientAbortException")) {
                return true;
            }
            if (current instanceof IOException ioEx) {
                String msg = ioEx.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("broken pipe") || lower.contains("connection reset") || lower.contains("connection aborted")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public record SalesPoint(LocalDate date, BigDecimal total) {}
}

