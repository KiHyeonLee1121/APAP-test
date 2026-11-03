package com.apap.backend.alert;

import com.apap.backend.cases.DetectionCaseRepository;
import com.apap.backend.cases.UserCase;
import com.apap.backend.cases.UserCaseRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실시간 알림 문구가 7월 회의 결정(in user_id, case_id → out msg)을 따르는지 검증.
 * AI가 문구를 보내면 그대로 쓰고, 안 보내면 구독한 케이스의 out_msg를 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertLiveCaseMessageTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    VideoSourceRepository videoSourceRepository;
    @Autowired
    DetectionCaseRepository detectionCaseRepository;
    @Autowired
    UserCaseRepository userCaseRepository;
    @Autowired
    AlertRepository alertRepository;

    private VideoSource saveCamera(String email) {
        User user = new User(email, "이름", "sub-" + email, "pic");
        user.changeRole(UserRole.MANAGER);
        userRepository.save(user);
        return videoSourceRepository.save(
                new VideoSource(user, VideoSourceType.CCTV, "카메라", "rtsp://cam"));
    }

    private void subscribeFirstCase(VideoSource camera) {
        // 서버 기동 시 시드된 감지 케이스 4종 중 첫 번째를 구독시킨다.
        userCaseRepository.save(new UserCase(
                camera.getUser(),
                detectionCaseRepository.findAll().get(0)));
    }

    @Test
    void 문구를_보내면_그대로_저장된다() throws Exception {
        VideoSource camera = saveCamera("live-msg@example.com");
        subscribeFirstCase(camera);

        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId()
                                + ",\"message\":\"AI가 보낸 문구\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("AI가 보낸 문구"));
    }

    @Test
    void 문구가_없으면_구독한_케이스의_out_msg를_쓴다() throws Exception {
        VideoSource camera = saveCamera("live-case@example.com");
        subscribeFirstCase(camera);
        String expected = detectionCaseRepository.findAll().get(0).getOutMsg();

        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(expected));
    }

    @Test
    void 구독한_케이스가_없으면_기본문구를_쓴다() throws Exception {
        VideoSource camera = saveCamera("live-default@example.com");

        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("비정상 행동이 감지되었습니다."));
    }

    @Test
    void 업로드영상으로는_알림이_생성되지_않는다() throws Exception {
        // 알림 내역에는 실시간 영상의 감지만 남아야 한다.
        User user = new User("live-upload@example.com", "이름", "sub-live-upload", "pic");
        user.changeRole(UserRole.MANAGER);
        userRepository.save(user);
        VideoSource uploaded = videoSourceRepository.save(
                new VideoSource(user, VideoSourceType.UPLOAD, "업로드영상", "uploads/videos/a.mp4"));

        long before = alertRepository.count();

        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + uploaded.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        assertThat(alertRepository.count()).isEqualTo(before);
    }
}
