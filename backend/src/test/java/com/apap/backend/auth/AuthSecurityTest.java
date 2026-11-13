package com.apap.backend.auth;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSecurityTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    VideoSourceRepository videoSourceRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    GoogleTokenVerifier googleTokenVerifier;

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createToken(user);
    }

    private User saveUser(String email, UserRole role) {
        User user = new User(email, "이름", "sub-" + email, "pic");
        user.changeRole(role);
        return userRepository.save(user);
    }

    @Test
    void 구글로그인_신규사용자_생성및토큰발급() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleTokenVerifier.GoogleAccount("sub1", "new@example.com", "신규", "pic"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"dummy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("new@example.com"));
    }

    @Test
    void me_토큰없으면_401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_토큰있으면_현재사용자반환() throws Exception {
        User user = saveUser("me@example.com", UserRole.MANAGER);
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@example.com"));
    }

    @Test
    void VIEWER는_영상등록_403() throws Exception {
        User viewer = saveUser("viewer@example.com", UserRole.VIEWER);
        mockMvc.perform(post("/api/videos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UPLOAD\",\"name\":\"v\",\"sourceUrl\":\"u\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void MANAGER는_영상등록_성공() throws Exception {
        User manager = saveUser("manager@example.com", UserRole.MANAGER);
        mockMvc.perform(post("/api/videos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UPLOAD\",\"name\":\"v\",\"sourceUrl\":\"u\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("v"));
    }

    @Test
    void 영상목록은_본인것만_조회() throws Exception {
        User owner = saveUser("owner@example.com", UserRole.MANAGER);
        User other = saveUser("other@example.com", UserRole.MANAGER);

        mockMvc.perform(post("/api/videos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UPLOAD\",\"name\":\"owned\",\"sourceUrl\":\"u\"}"))
                .andExpect(status().isOk());

        // 타인은 빈 목록
        mockMvc.perform(get("/api/videos").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 본인은 1건
        mockMvc.perform(get("/api/videos").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 영상_soft_delete_후_목록에서_제외() throws Exception {
        User manager = saveUser("del@example.com", UserRole.MANAGER);
        VideoSource video = videoSourceRepository.save(
                new VideoSource(manager, VideoSourceType.UPLOAD, "v", "u"));

        mockMvc.perform(get("/api/videos").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/api/videos/" + video.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 콜백은_인증없이_접근가능() throws Exception {
        // 공개 경로이므로 401이 아니어야 한다. 존재하지 않는 job이라 404가 반환된다.
        mockMvc.perform(post("/api/analysis/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":99999,\"status\":\"DONE\",\"errorMessage\":null,\"events\":[]}"))
                .andExpect(status().isNotFound());
    }
}
