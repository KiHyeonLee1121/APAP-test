package com.apap.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI에 JWT Bearer 인증 스킴을 등록한다.
 * Swagger 화면 우측 상단 Authorize 버튼에 로그인으로 받은 accessToken을 입력하면
 * 보호된 API를 테스트할 수 있다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI apapOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("APAP Backend API")
                        .description("비정상 행동 알람 플랫폼 백엔드 API. 인증: 구글 로그인(/api/auth/google)으로 받은 JWT를 Bearer 토큰으로 사용.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
