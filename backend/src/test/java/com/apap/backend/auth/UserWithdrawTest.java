package com.apap.backend.auth;

import com.apap.backend.user.User;
import com.apap.backend.user.UserRepository;
import com.apap.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원 탈퇴(DELETE /api/auth/me): soft delete + 익명화, 이후 조회/로그인에서 제외 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserWithdrawTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    GoogleTokenVerifier googleTokenVerifier;

    private User saveUser(String email, String sub) {
        User user = new User(email, "탈퇴대상", sub, "pic");
        user.changeRole(UserRole.MANAGER);
        return userRepository.save(user);
    }

    @Test
    void 탈퇴후_내정보조회는_실패한다() throws Exception {
        User user = saveUser("bye@example.com", "sub-bye");
        String bearer = "Bearer " + jwtTokenProvider.createToken(user);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 토큰이 아직 유효해도 탈퇴 계정은 조회되지 않는다
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 탈퇴계정은_익명화되고_같은_구글계정으로_재가입할_수_있다() throws Exception {
        User user = saveUser("rejoin@example.com", "sub-rejoin");
        Long oldId = user.getId();
        String bearer = "Bearer " + jwtTokenProvider.createToken(user);

        mockMvc.perform(delete("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        // 개인정보가 지워지고 soft delete 처리된다
        User withdrawn = userRepository.findById(oldId).orElseThrow();
        assertThat(withdrawn.isDeleted()).isTrue();
        assertThat(withdrawn.getEmail()).isEqualTo("withdrawn_" + oldId + "@deleted.local");
        assertThat(withdrawn.getGoogleSub()).isEqualTo("withdrawn_" + oldId);
        assertThat(userRepository.findByIdAndDeletedFalse(oldId)).isEmpty();

        // 같은 구글 계정으로 다시 로그인하면 새 계정이 만들어진다
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleTokenVerifier.GoogleAccount(
                        "sub-rejoin", "rejoin@example.com", "재가입", "pic"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"dummy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("rejoin@example.com"))
                .andExpect(jsonPath("$.data.user.id").value(org.hamcrest.Matchers.not(oldId.intValue())));
    }
}
