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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserRepository userRepository, GoogleTokenVerifier googleTokenVerifier,
                          JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtTokenProvider = jwtTokenProvider;
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

        User user = userRepository.findByGoogleSub(account.sub())
                .map(found -> {
                    found.syncProfile(displayName, account.pictureUrl());
                    return found;
                })
                .orElseGet(() -> userRepository.findByEmail(account.email())
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

        String accessToken = jwtTokenProvider.createToken(savedUser);
        return ApiResponse.ok(new LoginResponse(accessToken, UserResponse.from(savedUser)), "로그인 성공");
    }

    /** 현재 로그인 사용자 정보 */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        return ApiResponse.ok(UserResponse.from(user));
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
