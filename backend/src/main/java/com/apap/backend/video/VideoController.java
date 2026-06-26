package com.apap.backend.video;

import com.apap.backend.auth.AuthUser;
import com.apap.backend.common.ApiResponse;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoSourceRepository videoSourceRepository;
    private final UserRepository userRepository;
    private final Path uploadDir;

    public VideoController(
            VideoSourceRepository videoSourceRepository,
            UserRepository userRepository,
            @Value("${apap.upload-dir}") String uploadDir
    ) {
        this.videoSourceRepository = videoSourceRepository;
        this.userRepository = userRepository;
        this.uploadDir = Path.of(uploadDir);
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

        Files.createDirectories(uploadDir);
        String originalName = file.getOriginalFilename() == null ? "video" : file.getOriginalFilename();
        String savedName = UUID.randomUUID() + "-" + originalName;
        Path savedPath = uploadDir.resolve(savedName);
        file.transferTo(savedPath);

        VideoSource videoSource = new VideoSource(
                user,
                VideoSourceType.UPLOAD,
                originalName,
                savedPath.toString()
        );
        return ApiResponse.ok(VideoResponse.from(videoSourceRepository.save(videoSource)));
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
            VideoSourceStatus status
    ) {
        static VideoResponse from(VideoSource videoSource) {
            return new VideoResponse(
                    videoSource.getId(),
                    videoSource.getUser().getId(),
                    videoSource.getType(),
                    videoSource.getName(),
                    videoSource.getSourceUrl(),
                    videoSource.getStatus()
            );
        }
    }
}
