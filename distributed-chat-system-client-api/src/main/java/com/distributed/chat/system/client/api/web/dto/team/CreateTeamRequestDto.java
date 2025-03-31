package com.distributed.chat.system.client.api.web.dto.team;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "팀 등록 요청")
public class CreateTeamRequestDto {

    @NotNull
    @Schema(name = "teamName", description = "팀 이름", example = "FC서울")
    private String teamName;
}
