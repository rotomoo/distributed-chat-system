package com.distributed.chat.system.client.api.web.dto.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브라우저 PushSubscription.toJSON() 모양 그대로 {endpoint, keys: {p256dh, auth}}
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeRequestDto {

    @NotBlank
    private String endpoint;

    @Valid
    @NotNull
    private Keys keys;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Keys {

        @NotBlank
        private String p256dh;

        @NotBlank
        private String auth;
    }
}
