package com.distributed.chat.system.client.api.web.controller.v1.spec;

import com.distributed.chat.system.client.api.web.dto.channel.CreateChannelRequestDto;
import com.distributed.chat.system.client.api.web.dto.channel.OpenDmRequestDto;
import com.distributed.chat.system.client.api.web.dto.channel.ReadChannelRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "채널 API", description = "공개 채널, DM API")
public interface ChannelControllerSpec {

    @Operation(summary = "채널 목록 조회", description = "팀의 공개 채널 목록. joined 로 참여 여부, unreadCount 로 안 읽은 수(참여 안 한 채널은 0, 최대 100) 표시 (2003 팀 멤버가 아님)")
    ResponseEntity getChannels(@Parameter(description = "팀 ID") Long teamId);

    @Operation(summary = "채널 생성", description = "팀 안에 공개 채널을 만들고 바로 참여 (3001 이미 존재하는 채널, 2003 팀 멤버가 아님)")
    ResponseEntity createChannel(@Parameter(description = "팀 ID") Long teamId, CreateChannelRequestDto createChannelRequestDto);

    @Operation(summary = "채널 참여", description = "공개 채널에 참여. 이미 참여했으면 그대로 성공 (3002 없는 채널, 2003 팀 멤버가 아님)")
    ResponseEntity joinChannel(@Parameter(description = "채널 ID") Long channelId);

    @Operation(summary = "DM 목록 조회", description = "내 DM 목록과 상대방, 안 읽은 수(최대 100)")
    ResponseEntity getDms(@Parameter(description = "팀 ID") Long teamId);

    @Operation(summary = "DM 열기", description = "같은 팀 멤버와 DM. 이미 있으면 기존 DM (3004 자기 자신, 2003 상대가 팀 멤버가 아님)")
    ResponseEntity openDm(@Parameter(description = "팀 ID") Long teamId, OpenDmRequestDto openDmRequestDto);

    @Operation(summary = "채널 메시지 조회", description = "채널 멤버만. data = {list, hasNext}. list 는 오래된 순으로 size 개(최대 100), before 를 주면 그보다 이전, after 를 주면 그보다 뒤. hasNext 는 최신 / before 조회면 더 이전 메시지가, after 조회면 더 최신 메시지가 남아 있는지 (3003 채널 멤버 아님, 4001 잘못된 before/after 또는 둘 다 줌)")
    ResponseEntity getMessages(@Parameter(description = "채널 ID") Long channelId,
        @Parameter(description = "이 메시지 ID 보다 이전") String before,
        @Parameter(description = "이 메시지 ID 보다 뒤 (재접속 시 빈틈 채우기)") String after,
        @Parameter(description = "개수 (기본 20, 최대 100)") int size);

    @Operation(summary = "읽음 처리", description = "이 메시지까지 읽음. 이미 더 뒤까지 읽었으면 그대로 (3003 채널 멤버 아님, 4001 잘못된 메시지 ID)")
    ResponseEntity markRead(@Parameter(description = "채널 ID") Long channelId, ReadChannelRequestDto readChannelRequestDto);

    @Operation(summary = "안 읽은 메시지 따라잡기", description = "안 읽은 수(내가 보낸 것 제외, 상한 없음)와 안 읽은 첫 메시지 ID (구분선 위치). 읽음 처리는 하지 않음 (3003 채널 멤버 아님)")
    ResponseEntity getCatchUp(@Parameter(description = "채널 ID") Long channelId);
}
