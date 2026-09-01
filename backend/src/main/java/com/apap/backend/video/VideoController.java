package com.apap.backend.video;

import com.apap.backend.analysis.AnalysisService;
import com.apap.backend.auth.AuthUser;
import com.apap.backend.common.ApiResponse;
import com.apap.backend.storage.StorageService;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoSourceRepository videoSourceRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AnalysisService analysisService;
    private final boolean autoAnalyzeOnUpload;

    public VideoController(
            VideoSourceRepository videoSourceRepository,
            UserRepository userRepository,
            StorageService storageService,
            AnalysisService analysisService,
            @Value("${apap.analysis.auto-on-upload:true}") boolean autoAnalyzeOnUpload
    ) {
        this.videoSourceRepository = videoSourceRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.analysisService = analysisService;
        this.autoAnalyzeOnUpload = autoAnalyzeOnUpload;
    }

    @PostMapping
    public ApiResponse<VideoResponse> create(@Valid @RequestBody VideoRequest request,
                                             @AuthenticationPrincipal AuthUser authUser) {
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        VideoSource videoSource = new VideoSource(
                user,
                request.type(),
                request.name(),
                request.sourceUrl()
        );
        return ApiResponse.ok(VideoResponse.from(videoSourceRepository.save(videoSource)));
    }

    @PostMapping("/upload")
    public ApiResponse<VideoResponse> upload(@RequestParam MultipartFile file,
                                             @AuthenticationPrincipal AuthUser authUser) throws IOException {
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        String originalName = file.getOriginalFilename() == null ? "video" : file.getOriginalFilename();
        // 저장 모드(local/s3)에 따라 sourceUrl에 로컬 경로 또는 S3 키가 저장된다.
        String storedLocation = storageService.store(file);

        VideoSource videoSource = videoSourceRepository.save(new VideoSource(
                user,
                VideoSourceType.UPLOAD,
                originalName,
                storedLocation
        ));

        // 업로드만 해도 분석이 시작된다. AI 호출은 오래 걸리므로 작업만 만들어 두고
        // 실제 분석은 백그라운드에서 돌린 뒤, 클라이언트는 analysisJobId로 진행 상황을 조회한다.
        Long analysisJobId = null;
        if (autoAnalyzeOnUpload) {
            analysisJobId = analysisService.createPendingJob(videoSource).getId();
            analysisService.runAsync(analysisJobId);
        }

        return ApiResponse.ok(VideoResponse.from(videoSource, analysisJobId),
                autoAnalyzeOnUpload ? "업로드 완료. 분석이 시작되었습니다." : "업로드가 완료되었습니다.");
    }

    @GetMapping
    public ApiResponse<List<VideoResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        List<VideoResponse> videos = videoSourceRepository.findAllByUserIdOrderByIdDesc(authUser.id())
                .stream()
                .map(VideoResponse::from)
                .toList();
        return ApiResponse.ok(videos);
    }

    @GetMapping("/{videoId}")
    public ApiResponse<VideoResponse> get(@PathVariable Long videoId,
                                          @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(VideoResponse.from(findOwnedVideo(videoId, authUser)));
    }

    @PatchMapping("/{videoId}")
    public ApiResponse<VideoResponse> update(@PathVariable Long videoId,
                                             @Valid @RequestBody VideoUpdateRequest request,
                                             @AuthenticationPrincipal AuthUser authUser) {
        VideoSource videoSource = findOwnedVideo(videoId, authUser);
        videoSource.update(request.type(), request.name(), request.sourceUrl(), request.status());
        return ApiResponse.ok(VideoResponse.from(videoSourceRepository.save(videoSource)));
    }

    @DeleteMapping("/{videoId}")
    public ApiResponse<Void> delete(@PathVariable Long videoId,
                                    @AuthenticationPrincipal AuthUser authUser) {
        VideoSource videoSource = findOwnedVideo(videoId, authUser);
        // @SQLDelete에 의해 실제 삭제 대신 deleted=true로 soft delete 된다.
        videoSourceRepository.delete(videoSource);
        return ApiResponse.ok(null, "영상 소스가 삭제되었습니다.");
    }

    // 현재 사용자 소유의 영상만 조회. 없거나 타인 소유면 404로 처리해 정보 노출을 막는다.
    private VideoSource findOwnedVideo(Long videoId, AuthUser authUser) {
        VideoSource videoSource = videoSourceRepository.findById(videoId)
                .orElseThrow(() -> new EntityNotFoundException("영상 소스를 찾을 수 없습니다."));
        if (!videoSource.getUser().getId().equals(authUser.id())) {
            throw new EntityNotFoundException("영상 소스를 찾을 수 없습니다.");
        }
        return videoSource;
    }

    public record VideoRequest(
            VideoSourceType type,
            @NotBlank String name,
            @NotBlank String sourceUrl
    ) {
    }

    public record VideoUpdateRequest(
            VideoSourceType type,
            @NotBlank String name,
            @NotBlank String sourceUrl,
            VideoSourceStatus status
    ) {
    }

    public record VideoResponse(
            Long id,
            Long userId,
            VideoSourceType type,
            String name,
            String sourceUrl,
            VideoSourceStatus status,
            // 업로드 시 자동 생성된 분석 작업 id. 자동 분석이 꺼져 있거나 업로드 외 응답에서는 null
            Long analysisJobId
    ) {
        static VideoResponse from(VideoSource videoSource) {
            return from(videoSource, null);
        }

        static VideoResponse from(VideoSource videoSource, Long analysisJobId) {
            return new VideoResponse(
                    videoSource.getId(),
                    videoSource.getUser().getId(),
                    videoSource.getType(),
                    videoSource.getName(),
                    videoSource.getSourceUrl(),
                    videoSource.getStatus(),
                    analysisJobId
            );
        }
    }
}
