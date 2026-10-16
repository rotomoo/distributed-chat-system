package com.distributed.chat.system.client.api.web.dto.presence;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "팀 멤버 접속 상태")
public class UserPresenceDto {

    @Schema(name = "userId", description = "회원 ID", example = "1")
    private Long userId;

    @Schema(name = "status", description = "online : 기기 하나라도 active / away : 모든 기기가 입력 없음 / offline : 연결된 기기 없음",
        allowableValues = {"online", "away", "offline"}, example = "online")
    private String status;
}
