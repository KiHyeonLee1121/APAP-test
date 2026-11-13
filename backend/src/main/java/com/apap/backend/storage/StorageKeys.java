package com.apap.backend.storage;

import java.util.UUID;

/**
 * 저장 키 생성 유틸.
 * 팀 합의 키 형식: videos/{uuid}-{filename}
 * 라벨/케이스 등 분류 정보는 키에 넣지 않는다(DB 컬럼으로만 관리).
 */
public final class StorageKeys {

    private StorageKeys() {
    }

    public static String buildVideoKey(String originalFilename) {
        return "videos/" + UUID.randomUUID() + "-" + sanitize(originalFilename);
    }

    // 경로 구분자 제거 + 키에 안전하지 않은 문자를 _로 치환
    private static String sanitize(String originalFilename) {
        String name = (originalFilename == null || originalFilename.isBlank()) ? "video" : originalFilename;
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(lastSeparator + 1);
        name = name.replaceAll("[^A-Za-z0-9가-힣._-]", "_");
        return name.isBlank() ? "video" : name;
    }
}
