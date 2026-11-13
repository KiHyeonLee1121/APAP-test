package com.apap.backend.analysis;

import com.apap.backend.auth.GoogleTokenVerifier;
import com.apap.backend.auth.JwtTokenProvider;
import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import com.apap.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** apap.analysis.auto-on-upload=false면 업로드해도 분석이 시작되지 않아야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "apap.analysis.auto-on-upload=false")
@Transactional
class AutoAnalysisDisabledTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AnalysisJobRepository analysisJobRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    GoogleTokenVerifier googleTokenVerifier;

    @Test
    void 자동분석_비활성화시_작업이_생성되지_않는다() throws Exception {
        User manager = new User("off@example.com", "업로더", "sub-off", "pic");
        manager.changeRole(UserRole.MANAGER);
        userRepository.save(manager);

        long before = analysisJobRepository.count();

        MockMultipartFile file = new MockMultipartFile(
                "file", "no-auto.mp4", "video/mp4", "dummy-video".getBytes());

        mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisJobId").doesNotExist());

        assertThat(analysisJobRepository.count()).isEqualTo(before);
    }
}
