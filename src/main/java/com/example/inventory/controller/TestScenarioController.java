package com.example.inventory.controller;

import com.example.inventory.service.ReportingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tests")
public class TestScenarioController {

    private final ReportingService reportingService;

    public TestScenarioController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @PostMapping("/dirty-writer")
    public String dirtyWriter(@RequestParam String sku,
                              @RequestParam int delta) {
        reportingService.updateStockWithoutCommit(sku, delta);
        return "Writer done";
    }

    @GetMapping("/dirty-reader")
    public int dirtyReader(@RequestParam String sku) {
        return reportingService.readStockReadUncommitted(sku);
    }

    @GetMapping("/non-repeatable-read")
    public int nonRepeatableRead(@RequestParam String sku,
                                 @RequestParam(defaultValue = "10000") long delayMillis) {
        return reportingService.nonRepeatableRead(sku, delayMillis);
    }

    @GetMapping("/phantom-read")
    public long phantomRead(@RequestParam(defaultValue = "10000") long delayMillis) {
        return reportingService.phantomReadCount(delayMillis);
    }
}

