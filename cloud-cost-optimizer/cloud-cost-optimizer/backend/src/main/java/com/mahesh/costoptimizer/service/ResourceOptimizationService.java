package com.mahesh.costoptimizer.service;

import com.mahesh.costoptimizer.model.CostSummary.CostRecommendation;
import com.mahesh.costoptimizer.model.CostSummary.CostRecommendation.RecommendationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes AWS resources and generates cost optimization recommendations.
 *
 * Detection logic:
 *  - Idle EC2: average CPU < 5% over past 14 days
 *  - Oversized EC2: average CPU < 20% over past 14 days → right-size down
 *  - Unattached EBS volumes: no attachment state
 *  - Reserved Instance opportunity: on-demand instances running > 30 days
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceOptimizationService {

    private final Ec2Client ec2Client;
    private final CloudWatchClient cloudWatchClient;

    private static final double IDLE_CPU_THRESHOLD = 5.0;
    private static final double OVERSIZED_CPU_THRESHOLD = 20.0;
    private static final int ANALYSIS_DAYS = 14;

    @Cacheable(value = "recommendations", key = "#root.methodName")
    public List<CostRecommendation> getRecommendations() {
        List<CostRecommendation> recommendations = new ArrayList<>();

        try {
            recommendations.addAll(findIdleAndOversizedInstances());
            recommendations.addAll(findUnattachedVolumes());
            recommendations.addAll(findReservedInstanceOpportunities());
        } catch (Exception e) {
            log.error("Error generating optimization recommendations", e);
        }

        return recommendations;
    }

    /**
     * Find EC2 instances with low CPU utilization — idle or oversized.
     * Queries CloudWatch for average CPU over ANALYSIS_DAYS.
     */
    private List<CostRecommendation> findIdleAndOversizedInstances() {
        List<CostRecommendation> recs = new ArrayList<>();

        DescribeInstancesResponse response = ec2Client.describeInstances(
                DescribeInstancesRequest.builder()
                        .filters(Filter.builder()
                                .name("instance-state-name")
                                .values("running")
                                .build())
                        .build());

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                try {
                    double avgCpu = getAverageCpuUtilization(instance.instanceId());

                    if (avgCpu < IDLE_CPU_THRESHOLD) {
                        recs.add(CostRecommendation.builder()
                                .resourceId(instance.instanceId())
                                .resourceType("EC2")
                                .type(RecommendationType.IDLE_RESOURCE)
                                .description(String.format(
                                        "Instance %s (%s) has average CPU utilization of %.1f%% over %d days. " +
                                        "Consider stopping or terminating if unused.",
                                        instance.instanceId(), instance.instanceTypeAsString(), avgCpu, ANALYSIS_DAYS))
                                .estimatedMonthlySavings(estimateInstanceMonthlyCost(instance.instanceTypeAsString()))
                                .priority("HIGH")
                                .build());

                    } else if (avgCpu < OVERSIZED_CPU_THRESHOLD) {
                        recs.add(CostRecommendation.builder()
                                .resourceId(instance.instanceId())
                                .resourceType("EC2")
                                .type(RecommendationType.RIGHT_SIZING)
                                .description(String.format(
                                        "Instance %s (%s) has average CPU utilization of %.1f%% over %d days. " +
                                        "Consider downsizing to a smaller instance type.",
                                        instance.instanceId(), instance.instanceTypeAsString(), avgCpu, ANALYSIS_DAYS))
                                .estimatedMonthlySavings(estimateInstanceMonthlyCost(instance.instanceTypeAsString()) * 0.3)
                                .priority("MEDIUM")
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("Could not analyze instance {}: {}", instance.instanceId(), e.getMessage());
                }
            }
        }

        return recs;
    }

    private double getAverageCpuUtilization(String instanceId) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(ANALYSIS_DAYS, ChronoUnit.DAYS);

        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                .namespace("AWS/EC2")
                .metricName("CPUUtilization")
                .dimensions(Dimension.builder()
                        .name("InstanceId")
                        .value(instanceId)
                        .build())
                .startTime(startTime)
                .endTime(endTime)
                .period(86400) // 1-day granularity
                .statistics(Statistic.AVERAGE)
                .build();

        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
        return response.datapoints().stream()
                .mapToDouble(Datapoint::average)
                .average()
                .orElse(0.0);
    }

    /**
     * Find EBS volumes not attached to any instance — pure waste.
     */
    private List<CostRecommendation> findUnattachedVolumes() {
        List<CostRecommendation> recs = new ArrayList<>();

        DescribeVolumesResponse response = ec2Client.describeVolumes(
                DescribeVolumesRequest.builder()
                        .filters(Filter.builder()
                                .name("status")
                                .values("available") // available = not attached
                                .build())
                        .build());

        for (Volume volume : response.volumes()) {
            double estimatedCost = (volume.size() * 0.10); // ~$0.10/GB/month for gp2
            recs.add(CostRecommendation.builder()
                    .resourceId(volume.volumeId())
                    .resourceType("EBS")
                    .type(RecommendationType.STORAGE_OPTIMIZATION)
                    .description(String.format(
                            "EBS volume %s (%d GB, %s) is not attached to any instance. " +
                            "Snapshot and delete if data is not needed.",
                            volume.volumeId(), volume.size(), volume.volumeTypeAsString()))
                    .estimatedMonthlySavings(estimatedCost)
                    .priority("MEDIUM")
                    .build());
        }

        return recs;
    }

    /**
     * Find on-demand EC2 instances running for more than 30 days
     * that could benefit from Reserved Instance pricing (up to 72% savings).
     */
    private List<CostRecommendation> findReservedInstanceOpportunities() {
        List<CostRecommendation> recs = new ArrayList<>();
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        DescribeInstancesResponse response = ec2Client.describeInstances(
                DescribeInstancesRequest.builder()
                        .filters(
                                Filter.builder().name("instance-state-name").values("running").build(),
                                Filter.builder().name("instance-lifecycle").values("").build() // on-demand only
                        )
                        .build());

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                if (instance.launchTime().isBefore(thirtyDaysAgo)) {
                    double onDemandCost = estimateInstanceMonthlyCost(instance.instanceTypeAsString());
                    double reservedSavings = onDemandCost * 0.40; // ~40% savings with 1-year Reserved

                    recs.add(CostRecommendation.builder()
                            .resourceId(instance.instanceId())
                            .resourceType("EC2")
                            .type(RecommendationType.RESERVED_INSTANCE)
                            .description(String.format(
                                    "Instance %s (%s) has been running on-demand since %s. " +
                                    "Purchasing a 1-year Reserved Instance could save ~40%%.",
                                    instance.instanceId(), instance.instanceTypeAsString(),
                                    instance.launchTime().toString().substring(0, 10)))
                            .estimatedMonthlySavings(reservedSavings)
                            .priority("LOW")
                            .build());
                }
            }
        }

        return recs;
    }

    /**
     * Rough on-demand pricing estimates for common instance types.
     * In production, query the AWS Pricing API for accurate values.
     */
    private double estimateInstanceMonthlyCost(String instanceType) {
        return switch (instanceType) {
            case "t3.micro"   -> 7.59;
            case "t3.small"   -> 15.18;
            case "t3.medium"  -> 30.37;
            case "t3.large"   -> 60.74;
            case "t3.xlarge"  -> 121.47;
            case "m5.large"   -> 70.08;
            case "m5.xlarge"  -> 140.16;
            case "m5.2xlarge" -> 280.32;
            case "c5.large"   -> 62.05;
            case "c5.xlarge"  -> 124.10;
            case "r5.large"   -> 91.25;
            case "r5.xlarge"  -> 182.50;
            default -> 50.0; // conservative estimate for unknown types
        };
    }
}
