package com.apap.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 로컬 파일 시스템 저장소 (기본 모드).
 * S3 액세스 키 발급이 불가능한 로컬 개발 환경에서 사용한다.
 * sourceUrl에는 업로드 디렉터리를 포함한 로컬 경로가 저장되어
 * 같은 머신에서 실행되는 AI 서버가 그대로 파일을 읽을 수 있다.
 */
@Service
@ConditionalOnProperty(name = "apap.storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path uploadDir;

    public LocalStorageService(@Value("${apap.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
    }

    @Override
    public String store(MultipartFile file) throws IOException {
        String key = StorageKeys.buildVideoKey(file.getOriginalFilename());
        Path savedPath = uploadDir.resolve(key);
        Files.createDirectories(savedPath.getParent());
        Path absolutePath = savedPath.toAbsolutePath().normalize();
        file.transferTo(absolutePath);
        // AI 서버가 다른 작업 디렉터리(cwd)에서 실행되므로 상대경로 대신 절대경로를 저장한다.
        return absolutePath.toString();
    }
}
