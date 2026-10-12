package com.distributed.chat.system.client.api.web.dto.team;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "팀 멤버")
public class TeamMemberDto {

    @Schema(name = "userId", description = "회원 ID", example = "1")
    private Long userId;

    @Schema(name = "userName", description = "회원 이름", example = "alice")
    private String userName;
}
