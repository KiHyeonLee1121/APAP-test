package com.apap.backend.alert;

import com.apap.backend.auth.JwtTokenProvider;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import com.apap.backend.user.UserRole;
import com.apap.backend.video.VideoSource;
import com.apap.backend.video.VideoSourceRepository;
import com.apap.backend.video.VideoSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실시간 화면에서 "새 알림이 왔는지" 확인하는 경로 검증.
 * 프론트는 /unread-count를 짧은 주기로 폴링해 latestAlertId 변화로 알림음을 울린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertPollingTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    VideoSourceRepository videoSourceRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    ObjectMapper objectMapper;

    private User user;
    private VideoSource camera;

    private void setUpCamera(String email) {
        user = new User(email, "이름", "sub-" + email, "pic");
        user.changeRole(UserRole.MANAGER);
        userRepository.save(user);
        camera = videoSourceRepository.save(
                new VideoSource(user, VideoSourceType.CCTV, "카메라", "rtsp://cam"));
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createToken(user);
    }

    /** AI 서버가 실시간 감지 시 호출하는 경로를 그대로 사용한다. */
    private long fireLiveAlert() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long latestAlertId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/alerts/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("latestAlertId").asLong();
    }

    @Test
    void 알림이_없으면_미읽음0에_최신id는_null이다() throws Exception {
        setUpCamera("poll-empty@example.com");

        mockMvc.perform(get("/api/alerts/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.latestAlertId").doesNotExist());
    }

    @Test
    void 실시간_감지가_오면_최신id와_미읽음이_올라간다() throws Exception {
        setUpCamera("poll-new@example.com");

        long firstId = fireLiveAlert();
        assertThat(latestAlertId()).isEqualTo(firstId);

        // 다음 감지가 오면 최신 id가 바뀐다 → 프론트는 이 변화로 알림음을 울린다
        long secondId = fireLiveAlert();
        assertThat(secondId).isGreaterThan(firstId);
        assertThat(latestAlertId()).isEqualTo(secondId);

        mockMvc.perform(get("/api/alerts/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    void sinceId를_주면_그_이후_새_알림만_받는다() throws Exception {
        setUpCamera("poll-since@example.com");

        long firstId = fireLiveAlert();
        long secondId = fireLiveAlert();

        // 전체 조회는 2건
        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.data.length()").value(2));

        // 첫 알림 이후만 조회하면 1건
        mockMvc.perform(get("/api/alerts").param("sinceId", String.valueOf(firstId))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(secondId));

        // 최신까지 다 받은 뒤에는 새 알림이 없다
        mockMvc.perform(get("/api/alerts").param("sinceId", String.valueOf(secondId))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 알림_리셋후에는_폴링에서도_빠진다() throws Exception {
        setUpCamera("poll-reset@example.com");
        fireLiveAlert();

        mockMvc.perform(post("/api/alerts/reset").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/alerts/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.latestAlertId").doesNotExist());
    }
}
