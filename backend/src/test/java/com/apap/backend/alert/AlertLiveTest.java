package com.apap.backend.alert;

import com.apap.backend.auth.JwtTokenProvider;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import com.apap.backend.user.UserRole;
import com.apap.backend.video.VideoSource;
import com.apap.backend.video.VideoSourceRepository;
import com.apap.backend.video.VideoSourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AI 서버가 실시간 감지 시 호출하는 POST /api/alerts/live 경로 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertLiveTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    VideoSourceRepository videoSourceRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;

    private User saveManager(String email) {
        User user = new User(email, "이름", "sub-" + email, "pic");
        user.changeRole(UserRole.MANAGER);
        return userRepository.save(user);
    }

    @Test
    void 실시간_알림_호출시_카메라_소유자에게_알림이_생긴다() throws Exception {
        User owner = saveManager("live-alert@example.com");
        VideoSource camera = videoSourceRepository.save(
                new VideoSource(owner, VideoSourceType.CCTV, "cam", "rtsp://cam"));

        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("비정상 행동이 감지되었습니다."));

        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].message").value("비정상 행동이 감지되었습니다."));
    }

    @Test
    void 커스텀_메시지를_보내면_그대로_저장된다() throws Exception {
        User owner = saveManager("live-alert-msg@example.com");
        VideoSource camera = videoSourceRepository.save(
                new VideoSource(owner, VideoSourceType.CCTV, "cam2", "rtsp://cam2"));

        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId() + ",\"message\":\"쓰러짐이 감지되었습니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("쓰러짐이 감지되었습니다."));
    }

    @Test
    void 존재하지_않는_영상소스면_404() throws Exception {
        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 인증_없이도_호출된다() throws Exception {
        User owner = saveManager("live-alert-noauth@example.com");
        VideoSource camera = videoSourceRepository.save(
                new VideoSource(owner, VideoSourceType.CCTV, "cam3", "rtsp://cam3"));

        // Authorization 헤더 없이 호출해도 permitAll이라 통과해야 한다 (AI 서버는 JWT가 없음).
        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId() + "}"))
                .andExpect(status().isOk());
    }
}
