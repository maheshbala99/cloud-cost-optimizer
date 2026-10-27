package com.mahesh.costoptimizer.service;

import com.mahesh.costoptimizer.model.CostSummary;
import com.mahesh.costoptimizer.model.CostSummary.DailyCost;
import com.mahesh.costoptimizer.model.CostSummary.ServiceCost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetches cost data from AWS Cost Explorer API.
 *
 * Cost Explorer API notes:
 *  - Data lags by ~24 hours (yesterday is the most recent available)
 *  - Results are cached for 1 hour to avoid excessive API charges
 *    (Cost Explorer API itself has a cost per request)
 *  - All monetary values are in USD unless otherwise noted
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostExplorerService {

    private final CostExplorerClient costExplorerClient;
    private final ResourceOptimizationService optimizationService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Build the full cost summary for the dashboard.
     * Cached for 1 hour — Cost Explorer API charges ~$0.01 per request.
     */
    @Cacheable(value = "costSummary", key = "#root.methodName")
    public CostSummary getCostSummary() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfCurrentMonth = today.withDayOfMonth(1);
        LocalDate firstOfPreviousMonth = firstOfCurrentMonth.minusMonths(1);
        LocalDate endOfPreviousMonth = firstOfCurrentMonth.minusDays(1);

        log.info("Fetching cost summary from AWS Cost Explorer");

        double currentMonthTotal = getMonthTotal(firstOfCurrentMonth, today);
        double previousMonthTotal = getMonthTotal(firstOfPreviousMonth, endOfPreviousMonth);
        double changePercentage = previousMonthTotal > 0
                ? ((currentMonthTotal - previousMonthTotal) / previousMonthTotal) * 100
                : 0;

        List<DailyCost> dailyCosts = getDailyCosts(firstOfCurrentMonth, today);
        List<ServiceCost> serviceBreakdown = getServiceBreakdown(firstOfCurrentMonth, today);
        Map<String, Double> regionBreakdown = getRegionBreakdown(firstOfCurrentMonth, today);

        List<CostSummary.CostRecommendation> recommendations =
                optimizationService.getRecommendations();

        double estimatedMonthlySavings = recommendations.stream()
                .mapToDouble(CostSummary.CostRecommendation::getEstimatedMonthlySavings)
                .sum();

        return CostSummary.builder()
                .currentMonthTotal(round(currentMonthTotal))
                .previousMonthTotal(round(previousMonthTotal))
                .changePercentage(round(changePercentage))
                .dailyCosts(dailyCosts)
                .serviceBreakdown(serviceBreakdown)
                .regionBreakdown(regionBreakdown)
                .recommendations(recommendations)
                .estimatedMonthlySavings(round(estimatedMonthlySavings))
                .build();
    }

    private double getMonthTotal(LocalDate start, LocalDate end) {
        try {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(start.format(DATE_FMT))
                            .end(end.plusDays(1).format(DATE_FMT))
                            .build())
                    .granularity(Granularity.MONTHLY)
                    .metrics("UnblendedCost")
                    .build();

            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);
            return response.resultsByTime().stream()
                    .mapToDouble(r -> Double.parseDouble(
                            r.total().get("UnblendedCost").amount()))
                    .sum();
        } catch (Exception e) {
            log.error("Error fetching month total from Cost Explorer", e);
            return 0.0;
        }
    }

    private List<DailyCost> getDailyCosts(LocalDate start, LocalDate end) {
        try {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(start.format(DATE_FMT))
                            .end(end.plusDays(1).format(DATE_FMT))
                            .build())
                    .granularity(Granularity.DAILY)
                    .metrics("UnblendedCost")
                    .build();

            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);
            return response.resultsByTime().stream()
                    .map(r -> DailyCost.builder()
                            .date(r.timePeriod().start())
                            .amount(round(Double.parseDouble(r.total().get("UnblendedCost").amount())))
                            .currency("USD")
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching daily costs from Cost Explorer", e);
            return Collections.emptyList();
        }
    }

    private List<ServiceCost> getServiceBreakdown(LocalDate start, LocalDate end) {
        try {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(start.format(DATE_FMT))
                            .end(end.plusDays(1).format(DATE_FMT))
                            .build())
                    .granularity(Granularity.MONTHLY)
                    .metrics("UnblendedCost")
                    .groupBy(GroupDefinition.builder()
                            .type(GroupDefinitionType.DIMENSION)
                            .key("SERVICE")
                            .build())
                    .build();

            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);

            List<ServiceCost> services = new ArrayList<>();
            double total = 0;

            for (ResultByTime result : response.resultsByTime()) {
                for (Group group : result.groups()) {
                    double amount = Double.parseDouble(group.metrics().get("UnblendedCost").amount());
                    if (amount > 0.01) { // filter out zero-cost services
                        services.add(ServiceCost.builder()
                                .serviceName(group.keys().get(0))
                                .amount(round(amount))
                                .currency("USD")
                                .build());
                        total += amount;
                    }
                }
            }

            // Calculate percentages
            final double finalTotal = total;
            services.forEach(s -> s.setPercentage(round((s.getAmount() / finalTotal) * 100)));

            // Sort by amount descending
            services.sort(Comparator.comparingDouble(ServiceCost::getAmount).reversed());
            return services;
        } catch (Exception e) {
            log.error("Error fetching service breakdown from Cost Explorer", e);
            return Collections.emptyList();
        }
    }

    private Map<String, Double> getRegionBreakdown(LocalDate start, LocalDate end) {
        try {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(start.format(DATE_FMT))
                            .end(end.plusDays(1).format(DATE_FMT))
                            .build())
                    .granularity(Granularity.MONTHLY)
                    .metrics("UnblendedCost")
                    .groupBy(GroupDefinition.builder()
                            .type(GroupDefinitionType.DIMENSION)
                            .key("REGION")
                            .build())
                    .build();

            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);

            Map<String, Double> regionMap = new LinkedHashMap<>();
            for (ResultByTime result : response.resultsByTime()) {
                for (Group group : result.groups()) {
                    double amount = Double.parseDouble(group.metrics().get("UnblendedCost").amount());
                    if (amount > 0.01) {
                        regionMap.put(group.keys().get(0), round(amount));
                    }
                }
            }
            return regionMap;
        } catch (Exception e) {
            log.error("Error fetching region breakdown from Cost Explorer", e);
            return Collections.emptyMap();
        }
    }

    @Cacheable(value = "costHistory", key = "#months")
    public List<Map<String, Object>> getMonthlyHistory(int months) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(months).withDayOfMonth(1);

        try {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(start.format(DATE_FMT))
                            .end(today.format(DATE_FMT))
                            .build())
                    .granularity(Granularity.MONTHLY)
                    .metrics("UnblendedCost")
                    .build();

            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);
            return response.resultsByTime().stream()
                    .map(r -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("month", r.timePeriod().start().substring(0, 7)); // yyyy-MM
                        entry.put("amount", round(Double.parseDouble(
                                r.total().get("UnblendedCost").amount())));
                        return entry;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching monthly history from Cost Explorer", e);
            return Collections.emptyList();
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
