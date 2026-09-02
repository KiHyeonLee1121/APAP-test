package com.apap.backend.video;

import com.apap.backend.auth.GoogleTokenVerifier;
import com.apap.backend.auth.JwtTokenProvider;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import com.apap.backend.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 7월 회의 S3 전환 검증 (로컬 모드):
 * - 업로드 키 형식 videos/{uuid}-{filename}
 * - VideoSource.sourceUrl 저장 값
 * - AI 호출 시 video_path == sourceUrl (가짜 AI 서버로 실제 요청 본문 검증)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VideoUploadStorageTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    GoogleTokenVerifier googleTokenVerifier;

    // 테스트 설정의 ai-server-url(http://localhost:18000)을 대신 받는 가짜 AI 서버
    static HttpServer fakeAiServer;
    static final AtomicReference<String> capturedAiRequestBody = new AtomicReference<>();

    @BeforeAll
    static void startFakeAiServer() throws Exception {
        fakeAiServer = HttpServer.create(new InetSocketAddress(18000), 0);
        fakeAiServer.createContext("/predict/video", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                capturedAiRequestBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] response = "{\"prediction\":\"normal\",\"confidence\":0.12,\"source\":\"test\",\"status\":\"success\",\"message\":\"ok\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        fakeAiServer.start();
    }

    @AfterAll
    static void stopFakeAiServer() {
        if (fakeAiServer != null) {
            fakeAiServer.stop(0);
        }
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createToken(user);
    }

    private User saveManager() {
        User user = new User("uploader@example.com", "업로더", "sub-upload", "pic");
        user.changeRole(UserRole.MANAGER);
        return userRepository.save(user);
    }

    @Test
    void 용량제한이_해제되어_기본상한보다_큰_영상도_업로드된다() throws Exception {
        User manager = saveManager();
        // Spring 기본 상한은 파일 1MB / 요청 10MB. 그보다 큰 파일이 통과해야 한다.
        byte[] big = new byte[12 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big-video.mp4", "video/mp4", big);

        MvcResult result = mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("big-video.mp4"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        Path savedPath = Path.of(data.get("sourceUrl").asText());
        assertThat(Files.exists(savedPath)).isTrue();
        assertThat(Files.size(savedPath)).isEqualTo(big.length);
    }

    @Test
    void 업로드시_sourceUrl은_videos_uuid_filename_키를_포함한다() throws Exception {
        User manager = saveManager();
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.mp4", "video/mp4", "dummy-video".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("sample.mp4"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String sourceUrl = data.get("sourceUrl").asText();

        // 로컬 모드: 업로드 디렉터리 아래 videos/{uuid}-{filename} 구조로 저장된다
        Path savedPath = Path.of(sourceUrl);
        assertThat(savedPath.getParent().getFileName().toString()).isEqualTo("videos");
        assertThat(savedPath.getFileName().toString())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-sample\\.mp4");
        assertThat(Files.exists(savedPath)).isTrue();
    }

    @Test
    void 분석요청시_AI에_전달되는_video_path는_sourceUrl과_같다() throws Exception {
        User manager = saveManager();
        MockMultipartFile file = new MockMultipartFile(
                "file", "analyze-me.mp4", "video/mp4", "dummy-video".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode uploadData = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        long videoId = uploadData.get("id").asLong();
        String sourceUrl = uploadData.get("sourceUrl").asText();

        capturedAiRequestBody.set(null);
        mockMvc.perform(post("/api/analysis/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + videoId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        String aiRequestBody = capturedAiRequestBody.get();
        assertThat(aiRequestBody).isNotNull();
        String videoPath = objectMapper.readTree(aiRequestBody).get("video_path").asText();
        assertThat(videoPath).isEqualTo(sourceUrl);
    }
}
