package com.apap.backend.cases;

import com.apap.backend.analysis.AnalysisJob;
import com.apap.backend.analysis.AnalysisJobRepository;
import com.apap.backend.auth.GoogleTokenVerifier;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseAndLoginHistoryTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    DetectionCaseRepository detectionCaseRepository;
    @Autowired
    UserCaseRepository userCaseRepository;
    @Autowired
    VideoSourceRepository videoSourceRepository;
    @Autowired
    AnalysisJobRepository analysisJobRepository;

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
    void 구글로그인시_로그인이력이_기록된다() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleTokenVerifier.GoogleAccount("sub-h", "history@example.com", "이력", "pic"));

        MvcResult result = mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"dummy\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/auth/login-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].loggedInAt").isNotEmpty());
    }

    @Test
    void 케이스_시드_4종이_조회된다() throws Exception {
        User viewer = saveUser("case-list@example.com", UserRole.VIEWER);
        mockMvc.perform(get("/api/cases").header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].outMsg").isNotEmpty());
    }

    @Test
    void 유저케이스_등록시_outMsg가_응답된다() throws Exception {
        User manager = saveUser("case-sub@example.com", UserRole.MANAGER);
        Long caseId = detectionCaseRepository.findAllByOrderByIdAsc().get(0).getId();

        mockMvc.perform(post("/api/user-cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseId\":" + caseId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caseId").value(caseId))
                .andExpect(jsonPath("$.data.outMsg").isNotEmpty());

        // 중복 등록은 400
        mockMvc.perform(post("/api/user-cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseId\":" + caseId + "}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/user-cases").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 활성케이스가_있으면_알림메시지에_outMsg가_사용된다() throws Exception {
        User manager = saveUser("case-alert@example.com", UserRole.MANAGER);
        DetectionCase detectionCase = detectionCaseRepository.findAllByOrderByIdAsc().get(0);
        userCaseRepository.save(new UserCase(manager, detectionCase));

        VideoSource video = videoSourceRepository.save(
                new VideoSource(manager, VideoSourceType.UPLOAD, "v", "u"));
        AnalysisJob job = analysisJobRepository.save(new AnalysisJob(video));

        mockMvc.perform(post("/api/analysis/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + job.getId() + ",\"status\":\"DONE\",\"errorMessage\":null," +
                                "\"events\":[{\"eventType\":\"ABNORMAL\",\"severity\":\"HIGH\",\"confidenceScore\":0.9," +
                                "\"detectedAt\":null,\"snapshotUrl\":null,\"clipUrl\":null,\"resultJson\":null}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].message").value(detectionCase.getOutMsg()));
    }
}
