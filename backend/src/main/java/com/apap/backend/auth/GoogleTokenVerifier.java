package com.apap.backend.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;

/**
 * 구글 OIDC ID 토큰(JWT)을 구글 공개키로 검증하고 계정 정보를 추출한다.
 * 검증 항목: 서명, 만료, issuer(accounts.google.com), audience(우리 클라이언트 ID).
 */
@Component
public class GoogleTokenVerifier {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${apap.google.client-id:}") String clientId) {
        this.clientId = clientId;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleAccount verify(String idTokenString) {
        if (!StringUtils.hasText(clientId)) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID가 설정되지 않았습니다. 환경변수를 확인하세요.");
        }
        if (!StringUtils.hasText(idTokenString)) {
            throw new IllegalArgumentException("idToken이 필요합니다.");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (Exception e) {
            throw new IllegalStateException("구글 ID 토큰 검증 중 오류가 발생했습니다.", e);
        }
        if (idToken == null) {
            throw new IllegalArgumentException("유효하지 않은 구글 ID 토큰입니다.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new IllegalArgumentException("이메일이 확인되지 않은 구글 계정입니다.");
        }

        return new GoogleAccount(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name"),
                (String) payload.get("picture")
        );
    }

    public record GoogleAccount(String sub, String email, String name, String pictureUrl) {
    }
}
