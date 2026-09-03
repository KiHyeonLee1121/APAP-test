package com.apap.backend.auth;

import com.apap.backend.auth.GoogleTokenVerifier.GoogleAccount;
import com.apap.backend.common.ApiResponse;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginHistoryRepository loginHistoryRepository;

    public AuthController(UserRepository userRepository, GoogleTokenVerifier googleTokenVerifier,
                          JwtTokenProvider jwtTokenProvider, LoginHistoryRepository loginHistoryRepository) {
        this.userRepository = userRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginHistoryRepository = loginHistoryRepository;
    }

    /**
     * 구글 로그인.
     * 프론트엔드(Google Identity Services)가 받은 ID 토큰을 검증하고,
     * 해당 구글 계정으로 사용자를 find-or-create 한 뒤 서비스 토큰을 발급한다.
     */
    @PostMapping("/google")
    public ApiResponse<LoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleAccount account = googleTokenVerifier.verify(request.idToken());
        String displayName = StringUtils.hasText(account.name()) ? account.name() : account.email();

        // 탈퇴한 계정은 제외하고 찾는다(탈퇴 후 같은 구글 계정으로 재가입 가능).
        User user = userRepository.findByGoogleSubAndDeletedFalse(account.sub())
                .map(found -> {
                    found.syncProfile(displayName, account.pictureUrl());
                    return found;
                })
                .orElseGet(() -> userRepository.findByEmailAndDeletedFalse(account.email())
                        .map(existing -> {
                            // 이전에 가입된 이메일이면 해당 계정에 구글 계정을 연결
                            existing.linkGoogle(account.sub());
                            existing.syncProfile(displayName, account.pictureUrl());
                            return existing;
                        })
                        .orElseGet(() -> new User(
                                account.email(),
                                displayName,
                                account.sub(),
                                account.pictureUrl()
                        )));

        User savedUser = userRepository.save(user);

        // 로그인 이력 기록 (7월 회의: user_id + 로그인 시각)
        loginHistoryRepository.save(new LoginHistory(savedUser));

        String accessToken = jwtTokenProvider.createToken(savedUser);
        return ApiResponse.ok(new LoginResponse(accessToken, UserResponse.from(savedUser)), "로그인 성공");
    }

    /** 내 로그인 이력 조회(최신순). 본인 이력만 조회 가능. */
    @GetMapping("/login-history")
    public ApiResponse<List<LoginHistoryResponse>> loginHistory(@AuthenticationPrincipal AuthUser authUser) {
        List<LoginHistoryResponse> history = loginHistoryRepository.findAllByUserIdOrderByIdDesc(authUser.id())
                .stream()
                .map(LoginHistoryResponse::from)
                .toList();
        return ApiResponse.ok(history);
    }

    /** 현재 로그인 사용자 정보 */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        User user = userRepository.findByIdAndDeletedFalse(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        return ApiResponse.ok(UserResponse.from(user));
    }

    /**
     * 회원 탈퇴. 실제 삭제 대신 soft delete + 개인정보 익명화 처리한다.
     * 탈퇴 후에는 토큰이 남아 있어도 조회가 되지 않는다.
     */
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthUser authUser) {
        User user = userRepository.findByIdAndDeletedFalse(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        user.withdraw();
        userRepository.save(user);

        return ApiResponse.ok(null, "회원 탈퇴가 완료되었습니다.");
    }

    /**
     * 로그아웃. JWT는 무상태이므로 서버는 별도 세션을 지우지 않고,
     * 클라이언트가 저장된 토큰을 폐기하면 된다. (추후 토큰 블랙리스트로 확장 가능)
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null, "로그아웃 되었습니다.");
    }

    public record GoogleLoginRequest(
            @NotBlank String idToken
    ) {
    }

    public record LoginHistoryResponse(
            Long id,
            Long userId,
            LocalDateTime loggedInAt
    ) {
        static LoginHistoryResponse from(LoginHistory history) {
            return new LoginHistoryResponse(
                    history.getId(),
                    history.getUser().getId(),
                    history.getLoggedInAt()
            );
        }
    }

    public record LoginResponse(
            String accessToken,
            UserResponse user
    ) {
    }

    public record UserResponse(
            Long id,
            String email,
            String name,
            String pictureUrl,
            String role
    ) {
        static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getPictureUrl(),
                    user.getRole().name()
            );
        }
    }
}
