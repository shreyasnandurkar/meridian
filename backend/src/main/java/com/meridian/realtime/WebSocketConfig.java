package com.meridian.realtime;

import com.meridian.config.MeridianProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over a plain WebSocket at {@code /ws}. Clients only subscribe; the server publishes trip events to
 * {@code /topic/trips/{id}}. The HTTP upgrade is open, and {@link StompAuthInterceptor} authenticates the STOMP
 * CONNECT with the same JWT the REST API uses and checks trip membership on every SUBSCRIBE.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final MeridianProperties props;
    private final StompAuthInterceptor auth;

    public WebSocketConfig(MeridianProperties props, StompAuthInterceptor auth) {
        this.props = props;
        this.auth = auth;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(props.appBaseUrl());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();
        heartbeats.setPoolSize(1);
        heartbeats.setThreadNamePrefix("ws-heartbeat-");
        heartbeats.initialize();
        // heartbeats let both sides notice a dropped connection (laptop sleep, Wi-Fi switch) within ~20 s
        registry.enableSimpleBroker("/topic").setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeats);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }
}
