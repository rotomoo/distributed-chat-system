package com.distributed.chat.system.notification.web.dto;

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
