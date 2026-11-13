package com.apap.backend.auth;

import com.apap.backend.user.UserRole;

/**
 * 인증된 사용자 정보. JWT에서 복원되어 SecurityContext의 principal로 사용된다.
 * 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser user}로 주입받는다.
 */
public record AuthUser(Long id, String email, UserRole role) {
}
