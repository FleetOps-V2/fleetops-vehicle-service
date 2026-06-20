package com.fleetops.vehicle.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import java.time.Duration;

@Configuration
public class BedrockConfig {

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(
            @Value("${bedrock.region}") String region,
            @Value("${bedrock.access-key:}") String accessKey,
            @Value("${bedrock.secret-key:}") String secretKey) {

        AwsCredentialsProvider credentialsProvider = (accessKey != null && !accessKey.isEmpty())
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.create();

        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)))
                .build();
    }
}
