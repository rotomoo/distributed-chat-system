package com.distributed.chat.system.connection.status.web.controller.v1.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "접속 상태 API", description = "팀 멤버 접속 상태 (online / away / offline)")
public interface PresenceControllerSpec {

    @Operation(
        summary = "팀 멤버 접속 상태 조회",
        description = "팀 멤버 전체의 접속 상태. data = {list: [{userId, status}]}, userId 오름차순. "
            + "online = 기기 하나라도 active, away = 모든 기기가 입력 없음, offline = 연결된 기기 없음 (2003 팀 멤버가 아님, 1003 로그인 안 함)",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "성공",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = """
                            {
                              "code": 0,
                              "msg": "팀 접속 상태 조회",
                              "data": {
                                "list": [
                                  {"userId": 1, "status": "online"},
                                  {"userId": 2, "status": "away"},
                                  {"userId": 4, "status": "offline"}
                                ]
                              },
                              "success": true
                            }
                            """
                    )
                )
            ),
            @ApiResponse(
                responseCode = "2003",
                description = "팀 멤버가 아님 (HTTP 403)",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = """
                            {
                              "code": 2003,
                              "msg": "팀 멤버가 아닙니다.",
                              "data": {
                                "errorCodeName": "NOT_TEAM_MEMBER"
                              },
                              "success": false
                            }
                            """
                    )
                )
            )
        }
    )
    ResponseEntity getTeamPresence(@Parameter(description = "팀 ID") Long teamId);
}
