package com.apap.backend.cases;

import com.apap.backend.auth.AuthUser;
import com.apap.backend.common.ApiResponse;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 감지 케이스 API. 7월 회의 방침(CRUD 최소주의)에 따라
 * 케이스 목록 조회 / 유저 케이스 등록 / 내 케이스 조회만 제공한다.
 */
@RestController
public class CaseController {

    private final DetectionCaseRepository detectionCaseRepository;
    private final UserCaseRepository userCaseRepository;
    private final UserRepository userRepository;

    public CaseController(DetectionCaseRepository detectionCaseRepository,
                          UserCaseRepository userCaseRepository,
                          UserRepository userRepository) {
        this.detectionCaseRepository = detectionCaseRepository;
        this.userCaseRepository = userCaseRepository;
        this.userRepository = userRepository;
    }

    /** 감지 케이스 목록 조회 */
    @GetMapping("/api/cases")
    public ApiResponse<List<CaseResponse>> listCases() {
        List<CaseResponse> cases = detectionCaseRepository.findAllByOrderByIdAsc()
                .stream()
                .map(CaseResponse::from)
                .toList();
        return ApiResponse.ok(cases);
    }

    /** 유저 케이스 등록(구독). 응답에 케이스 out_msg 포함 (회의: in user_id, case_id → out msg) */
    @PostMapping("/api/user-cases")
    public ApiResponse<UserCaseResponse> subscribe(@Valid @RequestBody UserCaseRequest request,
                                                   @AuthenticationPrincipal AuthUser authUser) {
        DetectionCase detectionCase = detectionCaseRepository.findById(request.caseId())
                .orElseThrow(() -> new EntityNotFoundException("케이스를 찾을 수 없습니다."));
        if (userCaseRepository.existsByUserIdAndDetectionCaseId(authUser.id(), detectionCase.getId())) {
            throw new IllegalArgumentException("이미 등록된 케이스입니다.");
        }
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        UserCase saved = userCaseRepository.save(new UserCase(user, detectionCase));
        return ApiResponse.ok(UserCaseResponse.from(saved), "케이스가 등록되었습니다.");
    }

    /** 내 케이스 목록 조회 */
    @GetMapping("/api/user-cases")
    public ApiResponse<List<UserCaseResponse>> myCases(@AuthenticationPrincipal AuthUser authUser) {
        List<UserCaseResponse> cases = userCaseRepository.findAllByUserIdOrderByIdDesc(authUser.id())
                .stream()
                .map(UserCaseResponse::from)
                .toList();
        return ApiResponse.ok(cases);
    }

    public record UserCaseRequest(@NotNull Long caseId) {
    }

    public record CaseResponse(
            Long id,
            String name,
            String description,
            String outMsg
    ) {
        static CaseResponse from(DetectionCase detectionCase) {
            return new CaseResponse(
                    detectionCase.getId(),
                    detectionCase.getName(),
                    detectionCase.getDescription(),
                    detectionCase.getOutMsg()
            );
        }
    }

    public record UserCaseResponse(
            Long id,
            Long userId,
            Long caseId,
            String caseName,
            String outMsg,
            boolean active
    ) {
        static UserCaseResponse from(UserCase userCase) {
            return new UserCaseResponse(
                    userCase.getId(),
                    userCase.getUser().getId(),
                    userCase.getDetectionCase().getId(),
                    userCase.getDetectionCase().getName(),
                    userCase.getDetectionCase().getOutMsg(),
                    userCase.isActive()
            );
        }
    }
}
