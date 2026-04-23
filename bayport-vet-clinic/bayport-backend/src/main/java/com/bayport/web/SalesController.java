package com.bayport.web;

import com.bayport.dto.PosCheckoutRequest;
import com.bayport.dto.PosCheckoutResponse;
import com.bayport.dto.PosReceiptDto;
import com.bayport.entity.Sale;
import com.bayport.service.PosService;
import com.bayport.service.SalesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private final SalesService salesService;
    private final PosService posService;

    public SalesController(SalesService salesService, PosService posService) {
        this.salesService = salesService;
        this.posService = posService;
    }

    /**
     * Point-of-sale checkout — recorded as {@link Sale} with source POS (included in reports).
     * Path is under /api/sales so it never collides with static-resource resolution on /api/pos/*.
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public ResponseEntity<PosCheckoutResponse> posCheckout(@Valid @RequestBody PosCheckoutRequest body) {
        try {
            return ResponseEntity.ok(posService.checkout(body));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping
    public List<Sale> list() {
        return salesService.listSales();
    }

    @GetMapping("/{saleId}/receipt")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public ResponseEntity<PosReceiptDto> receipt(@PathVariable long saleId) {
        try {
            return ResponseEntity.ok(posService.receiptBySaleId(saleId));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/summary")
    public List<SalesService.SalesPoint> summary(@RequestParam(defaultValue = "7") int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        return salesService.summary(safeDays);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return salesService.subscribe();
    }
}

