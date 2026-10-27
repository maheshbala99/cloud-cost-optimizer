package com.mahesh.costoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Top-level cost summary returned to the dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostSummary {

    // Current month total spend
    private double currentMonthTotal;

    // Previous month total for comparison
    private double previousMonthTotal;

    // Month-over-month change percentage
    private double changePercentage;

    // Daily spend for the current month (used for trend chart)
    private List<DailyCost> dailyCosts;

    // Spend breakdown by AWS service
    private List<ServiceCost> serviceBreakdown;

    // Spend breakdown by AWS region
    private Map<String, Double> regionBreakdown;

    // Optimization recommendations
    private List<CostRecommendation> recommendations;

    // Total estimated monthly savings available
    private double estimatedMonthlySavings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCost {
        private String date;        // yyyy-MM-dd
        private double amount;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceCost {
        private String serviceName;
        private double amount;
        private double percentage;  // % of total
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostRecommendation {
        private String resourceId;
        private String resourceType;    // EC2, S3, RDS, etc.
        private RecommendationType type;
        private String description;
        private double estimatedMonthlySavings;
        private String priority;        // HIGH, MEDIUM, LOW

        public enum RecommendationType {
            IDLE_RESOURCE,          // Resource with <5% CPU utilization
            RIGHT_SIZING,           // Overprovisioned instance
            RESERVED_INSTANCE,      // On-demand instance that should be Reserved
            STORAGE_OPTIMIZATION,   // Unused EBS volumes / S3 lifecycle
            SAVINGS_PLAN            // Compute Savings Plan opportunity
        }
    }
}
