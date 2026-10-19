package com.distributed.chat.system.client.api.web.dto.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UnsubscribeRequestDto {

    @NotBlank
    private String endpoint;
}
