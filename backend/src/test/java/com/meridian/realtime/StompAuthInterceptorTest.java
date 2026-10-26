package com.meridian.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meridian.common.ApiException;
import com.meridian.trip.TripAccess;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class StompAuthInterceptorTest {

    private final UUID user = UUID.randomUUID();
    private final UUID trip = UUID.randomUUID();
    private final UUID otherTrip = UUID.randomUUID();
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final TripAccess access = mock(TripAccess.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(decoder, access);

    StompAuthInterceptorTest() {
        Jwt jwt = Jwt.withTokenValue("good").header("alg", "HS256").subject(user.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(decoder.decode("good")).thenReturn(jwt);
        when(decoder.decode("bad")).thenThrow(new BadJwtException("expired"));
        when(access.requireMember(otherTrip, user)).thenThrow(ApiException.notFound("Trip not found"));
    }

    private Message<byte[]> frame(StompCommand command, String auth, String destination,
            StompAuthInterceptor.UserPrincipal principal) {
        StompHeaderAccessor h = StompHeaderAccessor.create(command);
        if (auth != null) h.addNativeHeader("Authorization", auth);
        if (destination != null) h.setDestination(destination);
        if (principal != null) h.setUser(principal);
        h.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
    }

    @Test
    void connectNeedsAValidBearerToken() {
        Message<?> ok = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), channel);
        assertThat(StompHeaderAccessor.wrap(ok).getUser()).isEqualTo(new StompAuthInterceptor.UserPrincipal(user));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer bad", null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribeOnlyToTripsTheUserBelongsTo() {
        var me = new StompAuthInterceptor.UserPrincipal(user);
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/trips/" + trip, me), channel);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/trips/" + otherTrip, me), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/all", me), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/trips/" + trip, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void clientsCannotSend() {
        var me = new StompAuthInterceptor.UserPrincipal(user);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null, "/app/x", me), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
