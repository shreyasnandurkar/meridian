package com.meridian.realtime;

import com.meridian.common.ApiException;
import com.meridian.trip.TripAccess;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authorizes the WebSocket: CONNECT must carry {@code Authorization: Bearer <jwt>}, a SUBSCRIBE is allowed only to
 * {@code /topic/trips/{id}} of a trip the user is a member of, and clients may not SEND (they change things through
 * the REST API, which publishes the events).
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern TRIP_TOPIC = Pattern.compile("^/topic/trips/([0-9a-fA-F-]{36})$");

    /** The authenticated user on a STOMP session; its name is the user id. */
    public record UserPrincipal(UUID userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }

    private final JwtDecoder jwt;
    private final TripAccess access;

    public StompAuthInterceptor(JwtDecoder jwt, TripAccess access) {
        this.jwt = jwt;
        this.access = access;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) return message;
        switch (stomp.getCommand()) {
            case CONNECT, STOMP -> stomp.setUser(authenticate(stomp.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorizeSubscription(stomp);
            case SEND -> throw new MessageDeliveryException("Clients cannot send messages");
            default -> { }
        }
        return message;
    }

    private UserPrincipal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Sign in first: missing bearer token");
        }
        try {
            return new UserPrincipal(UUID.fromString(jwt.decode(header.substring(7).strip()).getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor stomp) {
        if (!(stomp.getUser() instanceof UserPrincipal user)) {
            throw new MessageDeliveryException("Not signed in");
        }
        String destination = stomp.getDestination();
        Matcher m = destination == null ? null : TRIP_TOPIC.matcher(destination);
        if (m == null || !m.matches()) throw new MessageDeliveryException("Unknown topic");
        try {
            access.requireMember(UUID.fromString(m.group(1)), user.userId());
        } catch (ApiException e) {
            throw new MessageDeliveryException("Trip not found");
        }
    }
}
