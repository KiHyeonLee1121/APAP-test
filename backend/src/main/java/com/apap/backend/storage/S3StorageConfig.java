package com.apap.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 모드에서만 S3Client 빈을 생성한다.
 * local 모드에서는 이 설정이 로드되지 않아 AWS 자격증명 없이도 앱이 기동된다.
 */
@Configuration
@ConditionalOnProperty(name = "apap.storage.mode", havingValue = "s3")
public class S3StorageConfig {

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(@Value("${apap.storage.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                // 액세스 키 발급 불가 계정 → EC2 인스턴스 프로파일 Role을 기본 체인으로 사용
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
