package com.distributed.chat.system.connection.status.web.controller.v1;

import com.distributed.chat.system.common.response.ResponseData;
import com.distributed.chat.system.connection.status.web.controller.v1.spec.PresenceControllerSpec;
import com.distributed.chat.system.connection.status.web.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/private/api/presence")
@RequiredArgsConstructor
@Slf4j
public class PresenceController implements PresenceControllerSpec {

    private final PresenceService presenceService;

    /**
     * 팀 멤버 접속 상태 (online / away / offline)
     *
     * @param teamId
     * @return
     */
    @Override
    @GetMapping
    public ResponseEntity<?> getTeamPresence(@RequestParam Long teamId) {
        ResponseData responseData = presenceService.getTeamPresence(teamId);

        return ResponseEntity.ok(responseData);
    }
}
