package com.apap.backend.storage;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * 업로드 파일 저장소 추상화.
 * apap.storage.mode 설정(local/s3)에 따라 구현체가 선택된다.
 */
public interface StorageService {

    /**
     * 파일을 저장하고 VideoSource.sourceUrl에 기록할 위치 문자열을 반환한다.
     * - local 모드: 업로드 디렉터리 기준 파일 경로
     * - s3 모드: S3 객체 키 (videos/{uuid}-{filename})
     */
    String store(MultipartFile file) throws IOException;

    /**
     * VideoSource.sourceUrl에 저장된 위치의 파일을 읽어 브라우저 재생용으로 반환한다.
     */
    StoredObject load(String sourceUrl) throws IOException;

    record StoredObject(
            Resource resource,
            String contentType,
            Long contentLength
    ) {
    }
}
