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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실시간 감지 시 화면 우상단 알림을 띄우기 위한 SSE 스트림 검증.
 * 폴링과 달리 감지 즉시 서버가 밀어 주는 경로다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertStreamTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    VideoSourceRepository videoSourceRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    AlertStreamService alertStreamService;

    private User user;
    private VideoSource camera;

    private void setUpCamera(String email, String cameraName) {
        user = new User(email, "이름", "sub-" + email, "pic");
        user.changeRole(UserRole.MANAGER);
        userRepository.save(user);
        camera = videoSourceRepository.save(
                new VideoSource(user, VideoSourceType.CCTV, cameraName, "rtsp://cam"));
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createToken(user);
    }

    private MvcResult openStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/alerts/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(request().asyncStarted())
                .andReturn();
        // 연결 직후 보내는 connected 이벤트가 바로 흘러나와야 화면이 연결을 확인할 수 있다
        assertThat(result.getResponse().getContentAsString()).contains("event:connected");
        return result;
    }

    private void fireLiveAlert() throws Exception {
        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId() + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void 스트림을_열면_연결이_등록되고_connected_이벤트가_온다() throws Exception {
        setUpCamera("stream-open@example.com", "정문 카메라");

        openStream();

        assertThat(alertStreamService.activeConnections(user.getId())).isEqualTo(1);
    }

    @Test
    void 실시간_감지가_오면_스트림으로_알림이_즉시_밀려온다() throws Exception {
        setUpCamera("stream-push@example.com", "후문 카메라");
        MvcResult stream = openStream();

        fireLiveAlert();

        // MockHttpServletResponse 기본 charset으로는 한글이 깨지므로 UTF-8로 읽는다
        String pushed = stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(pushed).contains("event:" + AlertStreamService.EVENT_ALERT);
        assertThat(pushed).contains("비정상 행동이 감지되었습니다.");
        // 우상단 알림에 어느 카메라인지 띄울 수 있어야 한다
        assertThat(pushed).contains("후문 카메라");
    }

    @Test
    void 실시간_알림_응답에_카메라_정보가_들어간다() throws Exception {
        setUpCamera("stream-payload@example.com", "주차장 카메라");

        mockMvc.perform(post("/api/alerts/live")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoSourceId\":" + camera.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoSourceId").value(camera.getId()))
                .andExpect(jsonPath("$.data.videoSourceName").value("주차장 카메라"));
    }

    @Test
    void 열린_화면이_없어도_실시간_알림_생성은_성공한다() throws Exception {
        setUpCamera("stream-none@example.com", "창고 카메라");

        // 스트림을 열지 않은 상태 — 저장은 되고 화면은 다음 폴링에서 받는다
        assertThat(alertStreamService.activeConnections(user.getId())).isZero();
        fireLiveAlert();

        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 끊긴_연결은_다음_전송에서_정리된다() {
        setUpCamera("stream-close@example.com", "로비 카메라");

        SseEmitter emitter = alertStreamService.subscribe(user.getId());
        assertThat(alertStreamService.activeConnections(user.getId())).isEqualTo(1);

        // 화면이 닫힌 상태를 만든다. (실제 운영에서는 컨테이너가 onCompletion으로 알려주지만,
        //  그 알림을 놓쳐도 다음 전송 시도에서 정리되어야 연결이 쌓이지 않는다)
        emitter.complete();
        alertStreamService.publish(user.getId(), "새 감지");

        assertThat(alertStreamService.activeConnections(user.getId())).isZero();
    }

    @Test
    void 탭을_여러개_열어도_살아있는_연결만_남는다() {
        setUpCamera("stream-multi@example.com", "다중 카메라");

        SseEmitter closed = alertStreamService.subscribe(user.getId());
        alertStreamService.subscribe(user.getId());
        assertThat(alertStreamService.activeConnections(user.getId())).isEqualTo(2);

        // 탭 하나만 닫아도 나머지 탭의 알림은 계속 가야 한다
        closed.complete();
        alertStreamService.publish(user.getId(), "새 감지");

        assertThat(alertStreamService.activeConnections(user.getId())).isEqualTo(1);
    }
}
