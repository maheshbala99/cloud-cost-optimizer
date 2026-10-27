package com.mahesh.costoptimizer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS SDK v2 client configuration.
 *
 * Credentials resolution order (DefaultCredentialsProvider):
 *  1. Environment variables: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
 *  2. System properties
 *  3. ~/.aws/credentials file
 *  4. EC2/ECS instance profile (when deployed to AWS)
 *
 * For local development, set AWS credentials in application.yml or
 * environment variables. Never commit credentials to version control.
 */
@Configuration
public class AwsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        // Use static credentials if explicitly configured, otherwise fall back to DefaultCredentialsProvider
        if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        return DefaultCredentialsProvider.create();
    }

    /**
     * Cost Explorer API — must use us-east-1 region regardless of actual resource region.
     * AWS Cost Explorer is a global service endpoint.
     */
    @Bean
    public CostExplorerClient costExplorerClient(AwsCredentialsProvider credentialsProvider) {
        return CostExplorerClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public Ec2Client ec2Client(AwsCredentialsProvider credentialsProvider) {
        return Ec2Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public CloudWatchClient cloudWatchClient(AwsCredentialsProvider credentialsProvider) {
        return CloudWatchClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
