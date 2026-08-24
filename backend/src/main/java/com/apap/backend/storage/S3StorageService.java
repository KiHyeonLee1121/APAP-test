package com.apap.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

/**
 * AWS S3 저장소 (apap.storage.mode=s3).
 * 자격증명은 코드/설정에 두지 않고 EC2 인스턴스 프로파일 Role로 자동 인식한다.
 * sourceUrl에는 풀 URL이 아닌 S3 객체 키(videos/{uuid}-{filename})만 저장하며,
 * AI 서버는 이 키로 S3에서 직접 영상을 읽는다.
 */
@Service
@ConditionalOnProperty(name = "apap.storage.mode", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3StorageService(S3Client s3Client,
                            @Value("${apap.storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String store(MultipartFile file) throws IOException {
        String key = StorageKeys.buildVideoKey(file.getOriginalFilename());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return key;
    }
}
