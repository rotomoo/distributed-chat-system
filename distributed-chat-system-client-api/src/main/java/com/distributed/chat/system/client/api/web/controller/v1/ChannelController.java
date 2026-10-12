package com.distributed.chat.system.client.api.web.controller.v1;

import com.distributed.chat.system.client.api.web.controller.v1.spec.ChannelControllerSpec;
import com.distributed.chat.system.client.api.web.dto.channel.CreateChannelRequestDto;
import com.distributed.chat.system.client.api.web.dto.channel.OpenDmRequestDto;
import com.distributed.chat.system.client.api.web.service.ChannelService;
import com.distributed.chat.system.common.response.ResponseData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/private/api/channel")
@RequiredArgsConstructor
@Slf4j
public class ChannelController implements ChannelControllerSpec {

    private final ChannelService channelService;

    /**
     * 팀의 공개 채널 목록 (참여 여부 포함)
     *
     * @param teamId
     * @return
     */
    @Override
    @GetMapping("/channels")
    public ResponseEntity<?> getChannels(@RequestParam Long teamId) {
        ResponseData responseData = channelService.getChannels(teamId);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 공개 채널 생성
     *
     * @param teamId
     * @param createChannelRequestDto
     * @return
     */
    @Override
    @PostMapping("/channel")
    public ResponseEntity<?> createChannel(@RequestParam Long teamId,
        @RequestBody @Valid CreateChannelRequestDto createChannelRequestDto) {
        ResponseData responseData = channelService.createChannel(teamId, createChannelRequestDto);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 공개 채널 참여
     *
     * @param channelId
     * @return
     */
    @Override
    @PostMapping("/{channelId}/join")
    public ResponseEntity<?> joinChannel(@PathVariable Long channelId) {
        ResponseData responseData = channelService.joinChannel(channelId);

        return ResponseEntity.ok(responseData);
    }

    /**
     * 내 DM 목록
     *
     * @param teamId
     * @return
     */
    @Override
    @GetMapping("/dms")
    public ResponseEntity<?> getDms(@RequestParam Long teamId) {
        ResponseData responseData = channelService.getDms(teamId);

        return ResponseEntity.ok(responseData);
    }

    /**
     * DM 열기 (없으면 만들고, 있으면 기존 DM)
     *
     * @param teamId
     * @param openDmRequestDto
     * @return
     */
    @Override
    @PostMapping("/dm")
    public ResponseEntity<?> openDm(@RequestParam Long teamId, @RequestBody @Valid OpenDmRequestDto openDmRequestDto) {
        ResponseData responseData = channelService.openDm(teamId, openDmRequestDto);

        return ResponseEntity.ok(responseData);
    }
}
