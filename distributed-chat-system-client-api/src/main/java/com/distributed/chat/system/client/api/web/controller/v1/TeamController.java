package com.distributed.chat.system.client.api.web.controller.v1;

import com.distributed.chat.system.client.api.web.controller.v1.spec.TeamControllerSpec;
import com.distributed.chat.system.client.api.web.dto.team.CreateTeamRequestDto;
import com.distributed.chat.system.client.api.web.dto.team.GetTeamsRequestDto;
import com.distributed.chat.system.client.api.web.service.TeamService;
import com.distributed.chat.system.common.response.ResponseData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/private/api/team")
@RequiredArgsConstructor
@Slf4j
public class TeamController implements TeamControllerSpec {

    private final TeamService teamService;

    /**
     * 팀 목록 조회
     *
     * @param getTeamsRequestDto
     * @return
     */
    @Override
    @GetMapping("/teams")
    public ResponseEntity<?> getTeams(@ModelAttribute @Valid GetTeamsRequestDto getTeamsRequestDto) {
        ResponseData responseData = teamService.getTeams(getTeamsRequestDto);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 팀 등록
     *
     * @param createTeamRequestDto
     * @return
     */
    @Override
    @PostMapping("/team")
    public ResponseEntity<?> createTeam(@RequestBody @Valid CreateTeamRequestDto createTeamRequestDto) {
        ResponseData responseData = teamService.createTeam(createTeamRequestDto);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 내가 속한 팀 목록
     *
     * @return
     */
    @Override
    @GetMapping("/teams/me")
    public ResponseEntity<?> getMyTeams() {
        ResponseData responseData = teamService.getMyTeams();

        return ResponseEntity.ok(responseData);
    }

    /**
     * 팀 가입
     *
     * @param teamId
     * @return
     */
    @Override
    @PostMapping("/{teamId}/join")
    public ResponseEntity<?> joinTeam(@PathVariable Long teamId) {
        ResponseData responseData = teamService.joinTeam(teamId);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 팀 멤버 목록
     *
     * @param teamId
     * @return
     */
    @Override
    @GetMapping("/{teamId}/members")
    public ResponseEntity<?> getTeamMembers(@PathVariable Long teamId) {
        ResponseData responseData = teamService.getTeamMembers(teamId);

        return ResponseEntity.ok(responseData);
    }
}
