package com.apap.backend.video;

import com.apap.backend.alert.Alert;
import com.apap.backend.alert.AlertRepository;
import com.apap.backend.analysis.AnalysisJob;
import com.apap.backend.analysis.AnalysisJobRepository;
import com.apap.backend.auth.GoogleTokenVerifier;
import com.apap.backend.auth.JwtTokenProvider;
import com.apap.backend.event.DetectionEvent;
import com.apap.backend.event.DetectionEventRepository;
import com.apap.backend.event.DetectionEventType;
import com.apap.backend.event.Severity;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import com.apap.backend.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 저장된 영상 / 알림 리셋:
 * 사용자 화면에서는 사라지지만 DB 행은 남아야 한다(deleted=true로만 표시).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResetTest {

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
    AlertRepository alertRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    EntityManager entityManager;

    @MockBean
    GoogleTokenVerifier googleTokenVerifier;

    private User saveManager(String email) {
        User user = new User(email, "사용자", "sub-" + email, "pic");
        user.changeRole(UserRole.MANAGER);
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createToken(user);
    }

    /** 영상 1건 + 분석작업 + 감지이벤트(ABNORMAL) + 알림 1건을 만든다. */
    private VideoSource seedData(User user) {
        VideoSource video = videoSourceRepository.save(
                new VideoSource(user, VideoSourceType.UPLOAD, "영상", "uploads/videos/a.mp4"));
        AnalysisJob job = analysisJobRepository.save(new AnalysisJob(video));
        DetectionEvent event = detectionEventRepository.save(new DetectionEvent(
                job, DetectionEventType.ABNORMAL, Severity.HIGH, 0.91,
                LocalDateTime.now(), null, null, null));
        alertRepository.save(new Alert(event, user, "비정상 행동이 감지되었습니다."));
        return video;
    }

    // 네이티브 벌크 UPDATE 결과를 다시 읽기 전에 영속성 컨텍스트를 비운다.
    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 영상리셋하면_목록이_비고_DB에는_행이_남는다() throws Exception {
        User user = saveManager("reset-video@example.com");
        VideoSource video = seedData(user);
        Long videoId = video.getId();
        sync();

        mockMvc.perform(post("/api/videos/reset").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hiddenCount").value(1));
        sync();

        // 화면에서는 사라진다
        mockMvc.perform(get("/api/videos").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(0));

        // DB 행은 남아 있고 deleted 표시만 바뀐다
        Object deleted = entityManager
                .createNativeQuery("SELECT deleted FROM video_sources WHERE id = :id")
                .setParameter("id", videoId)
                .getSingleResult();
        assertThat(deleted.toString()).isIn("true", "1");
    }

    @Test
    void 영상리셋하면_이벤트와_분석작업_대시보드에서도_빠진다() throws Exception {
        User user = saveManager("reset-cascade@example.com");
        seedData(user);
        sync();

        mockMvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/videos/reset").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
        sync();

        mockMvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/analysis/jobs").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/dashboard/summary").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.videos").value(0))
                .andExpect(jsonPath("$.data.analysisJobs").value(0))
                .andExpect(jsonPath("$.data.abnormalEvents").value(0));
    }

    @Test
    void 알림리셋하면_목록이_비고_미읽음도_0이_된다() throws Exception {
        User user = saveManager("reset-alert@example.com");
        seedData(user);
        sync();

        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/alerts/reset").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hiddenCount").value(1));
        sync();

        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/dashboard/summary").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.unreadAlerts").value(0));

        // 알림 행도 DB에 남는다
        Number remaining = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM alerts WHERE receiver_id = :id")
                .setParameter("id", user.getId())
                .getSingleResult();
        assertThat(remaining.intValue()).isEqualTo(1);
    }

    @Test
    void 영상리셋과_알림리셋은_서로_독립이다() throws Exception {
        User user = saveManager("reset-independent@example.com");
        seedData(user);
        sync();

        // 영상만 리셋 → 알림은 그대로
        mockMvc.perform(post("/api/videos/reset").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
        sync();
        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(1));

        // 알림만 리셋한 새 사용자 → 영상은 그대로
        User other = saveManager("reset-independent2@example.com");
        seedData(other);
        sync();
        mockMvc.perform(post("/api/alerts/reset").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk());
        sync();
        mockMvc.perform(get("/api/videos").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 타인의_영상과_알림은_리셋되지_않는다() throws Exception {
        User me = saveManager("reset-me@example.com");
        User other = saveManager("reset-other@example.com");
        seedData(me);
        seedData(other);
        sync();

        mockMvc.perform(post("/api/videos/reset").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hiddenCount").value(1));
        mockMvc.perform(post("/api/alerts/reset").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hiddenCount").value(1));
        sync();

        // 타인 데이터는 그대로 보인다
        mockMvc.perform(get("/api/videos").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
