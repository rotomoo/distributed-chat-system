package com.distributed.chat.system.client.api.web.controller.v1.spec;

import com.distributed.chat.system.client.api.web.dto.team.GetTeamsRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "팀 API", description = "팀 API")
public interface TeamControllerSpec {

    @Operation(
        summary = "팀 목록 조회",
        description = "로그인 이후 채널 진입전 팀 목록 조회",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "성공",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = GetTeamsResponseDto.class)
                )
            ),
            @ApiResponse(
                responseCode = "500",
                description = "enum 필터 스펠링 확인, pagetNumber, pageSize 0 확인",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = """
                            {
                                "timestamp": "2025-03-28T02:32:06.297+00:00",
                                "status": 500,
                                "error": "Internal Server Error",
                                "path": "/v1/private/api/team/teams"
                            }
                            """
                    )
                )
            )
        })
    ResponseEntity getTeams(@ParameterObject GetTeamsRequestDto getTeamsRequestDto);
}
