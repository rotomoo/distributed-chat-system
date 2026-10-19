package com.distributed.chat.system.client.api.web.controller.v1.spec;

import com.distributed.chat.system.client.api.web.dto.notification.SubscribeRequestDto;
import com.distributed.chat.system.client.api.web.dto.notification.UnsubscribeRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "웹 푸시 구독 API", description = "탭을 닫아도 오는 알림 (구독은 여기서, 보내기는 notification-consumer)")
public interface NotificationControllerSpec {

    @Operation(
        summary = "VAPID 공개키 조회",
        description = "브라우저가 pushManager.subscribe 의 applicationServerKey 로 쓸 공개키 (base64url). "
            + "notification-consumer 가 아직 키를 만들지 않았으면 5002 (1003 로그인 안 함)",
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
                              "msg": "VAPID 공개키 조회",
                              "data": {"publicKey": "(base64url, 비압축 P-256 공개키 65바이트)"},
                              "success": true
                            }
                            """
                    )
                )
            )
        }
    )
    ResponseEntity getVapidPublicKey();

    @Operation(
        summary = "웹 푸시 구독",
        description = "브라우저 PushSubscription.toJSON() 그대로. 같은 endpoint 면 주인 / 키를 갱신한다. "
            + "허용하지 않은 푸시 서비스 호스트거나 키 형식이 틀리면 5001",
        responses = {
            @ApiResponse(
                responseCode = "5001",
                description = "잘못된 푸시 구독 (HTTP 400)",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = """
                            {
                              "code": 5001,
                              "msg": "잘못된 푸시 구독입니다.",
                              "data": {
                                "errorCodeName": "INVALID_PUSH_SUBSCRIPTION"
                              },
                              "success": false
                            }
                            """
                    )
                )
            )
        }
    )
    ResponseEntity subscribe(SubscribeRequestDto subscribeRequestDto);

    @Operation(summary = "웹 푸시 구독 해제", description = "내 구독만 지운다. 없으면 그대로 성공")
    ResponseEntity unsubscribe(UnsubscribeRequestDto unsubscribeRequestDto);
}
