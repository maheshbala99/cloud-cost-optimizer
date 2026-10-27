package com.mahesh.costoptimizer.controller;

import com.mahesh.costoptimizer.model.CostSummary;
import com.mahesh.costoptimizer.service.CostExplorerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing cost data to the React dashboard.
 *
 * GET /api/costs/summary           - Full dashboard data (current month, breakdown, recommendations)
 * GET /api/costs/history?months=6  - Monthly totals for trend chart
 * GET /api/costs/recommendations   - Optimization recommendations only
 */
@RestController
@RequestMapping("/api/costs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CostController {

    private final CostExplorerService costExplorerService;

    /**
     * Main endpoint — returns everything the dashboard needs in one call.
     * Response is cached for 1 hour on the service layer.
     */
    @GetMapping("/summary")
    public ResponseEntity<CostSummary> getCostSummary() {
        return ResponseEntity.ok(costExplorerService.getCostSummary());
    }

    /**
     * Monthly spend history for the trend line chart.
     * @param months number of past months to include (default 6)
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyHistory(
            @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(costExplorerService.getMonthlyHistory(months));
    }

    /**
     * Optimization recommendations for the recommendations panel.
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<CostSummary.CostRecommendation>> getRecommendations() {
        CostSummary summary = costExplorerService.getCostSummary();
        return ResponseEntity.ok(summary.getRecommendations());
    }
}
