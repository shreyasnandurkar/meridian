package com.meridian.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.config.MeridianProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    OpenAiCompatibleAiClient aiClient(MeridianProperties props, ObjectMapper json, AiCallLogRepository callLog) {
        return new OpenAiCompatibleAiClient(props.ai(), json, callLog);
    }
}
