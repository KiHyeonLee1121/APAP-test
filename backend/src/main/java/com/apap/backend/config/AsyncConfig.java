package com.apap.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 업로드 시 자동 분석을 백그라운드로 돌리기 위한 비동기 설정.
 * AI 호출은 영상 길이에 따라 오래 걸리므로 업로드 응답을 막지 않는다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String ANALYSIS_EXECUTOR = "analysisExecutor";

    @Bean(ANALYSIS_EXECUTOR)
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("apap-analysis-");
        executor.initialize();
        return executor;
    }
}
