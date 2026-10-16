package com.distributed.chat.system.client.api.web.controller.v1.spec;

import com.distributed.chat.system.client.api.web.dto.team.CreateTeamRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
        }
    )
    ResponseEntity getTeams(@ParameterObject GetTeamsRequestDto getTeamsRequestDto);

    @Operation(
        summary = "팀 등록",
        description = "로그인 이후 팀 등록",
        responses = {
            @ApiResponse(
                responseCode = "2001",
                description = "이미 존재하는 팀 이름",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = """
                            {
                              "code": 2001,
                              "msg": "이미 존재하는 팀입니다.",
                              "data": {
                                "errorCodeName": "ALREADY_USING_TEAM_NAME"
                              },
                              "success": false
                            }
                            """
                    )
                )
            )
        }
    )
    ResponseEntity createTeam(CreateTeamRequestDto createTeamsRequestDto);

    @Operation(summary = "내 팀 목록 조회", description = "내가 멤버인 팀(워크스페이스) 목록")
    ResponseEntity getMyTeams();

    @Operation(summary = "팀 가입", description = "팀 멤버가 되고 #general 채널에 참여한다. 이미 멤버면 그대로 성공 (2002 없는 팀)")
    ResponseEntity joinTeam(@Parameter(description = "팀 ID") Long teamId);

    @Operation(summary = "팀 멤버 목록 조회", description = "DM 상대 고르기. 접속 상태는 GET /v1/private/api/presence?teamId= (2003 팀 멤버가 아님)")
    ResponseEntity getTeamMembers(@Parameter(description = "팀 ID") Long teamId);
}
