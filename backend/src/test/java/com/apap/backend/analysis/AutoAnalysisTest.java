package com.apap.backend.analysis;

import com.apap.backend.auth.GoogleTokenVerifier;
import com.apap.backend.auth.JwtTokenProvider;
import com.apap.backend.event.DetectionEventRepository;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import com.apap.backend.user.UserRole;
import com.apap.backend.video.VideoSourceRepository;
import com.apap.backend.video.VideoSourceStatus;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드만 해도 분석이 자동으로 시작되는지 검증한다.
 * 자동 분석은 비동기지만, 테스트에서는 SyncAnalysisExecutorConfig로 동기 실행된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AutoAnalysisTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    VideoSourceRepository videoSourceRepository;
    @Autowired
    AnalysisJobRepository analysisJobRepository;
    @Autowired
    DetectionEventRepository detectionEventRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    GoogleTokenVerifier googleTokenVerifier;

    // 테스트 설정의 ai-server-url(http://localhost:18000)을 대신 받는 가짜 AI 서버
    static HttpServer fakeAiServer;

    @BeforeAll
    static void startFakeAiServer() throws Exception {
        fakeAiServer = HttpServer.create(new InetSocketAddress(18000), 0);
        fakeAiServer.createContext("/predict/video", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            byte[] response = ("{\"prediction\":\"abnormal\",\"confidence\":0.93,"
                    + "\"source\":\"test\",\"status\":\"success\",\"message\":null}")
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

    private User saveManager(String email) {
        User user = new User(email, "업로더", "sub-" + email, "pic");
        user.changeRole(UserRole.MANAGER);
        return userRepository.save(user);
    }

    private MvcResult upload(User user, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "video/mp4", "dummy-video".getBytes());
        return mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createToken(user)))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void 업로드만_해도_분석작업이_자동생성된다() throws Exception {
        User manager = saveManager("auto1@example.com");
        long before = analysisJobRepository.count();

        MvcResult result = upload(manager, "auto-1.mp4");
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");

        // 응답에 analysisJobId가 실려 클라이언트가 진행 상황을 조회할 수 있어야 한다
        assertThat(data.get("analysisJobId").isNull()).isFalse();
        assertThat(analysisJobRepository.count()).isEqualTo(before + 1);

        AnalysisJob job = analysisJobRepository.findById(data.get("analysisJobId").asLong()).orElseThrow();
        assertThat(job.getVideoSource().getId()).isEqualTo(data.get("id").asLong());
    }

    /** 자동 분석은 백그라운드에서 도므로 완료될 때까지 잠시 기다린다. */
    private AnalysisJob awaitCompletion(long jobId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            AnalysisJob job = analysisJobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() != AnalysisJobStatus.PENDING) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("자동 분석이 10초 안에 끝나지 않았습니다. jobId=" + jobId);
    }

    @Test
    void 자동분석_성공시_이벤트가_저장되고_상태가_READY로_바뀐다() throws Exception {
        User manager = saveManager("auto2@example.com");
        long eventsBefore = detectionEventRepository.count();

        MvcResult result = upload(manager, "auto-2.mp4");
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");

        AnalysisJob job = awaitCompletion(data.get("analysisJobId").asLong());
        assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.DONE);

        // AI가 abnormal을 돌려주므로 감지 이벤트가 쌓여야 한다
        assertThat(detectionEventRepository.count()).isEqualTo(eventsBefore + 1);

        // 분석이 끝나면 영상 상태가 ANALYZING → READY 로 정리된다
        assertThat(videoSourceRepository.findById(data.get("id").asLong()).orElseThrow().getStatus())
                .isEqualTo(VideoSourceStatus.READY);
    }

    @Test
    void URL_등록만_한_영상은_자동분석되지_않는다() throws Exception {
        User manager = saveManager("auto3@example.com");
        long before = analysisJobRepository.count();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/videos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createToken(manager))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CCTV\",\"name\":\"cam\",\"sourceUrl\":\"rtsp://cam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisJobId").doesNotExist());

        assertThat(analysisJobRepository.count()).isEqualTo(before);
    }
}
