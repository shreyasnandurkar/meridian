package com.meridian.user;

import java.util.UUID;

public record UserDto(UUID id, String email, String displayName) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
