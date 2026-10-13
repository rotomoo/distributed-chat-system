package com.distributed.chat.system.kafka.consumer.feign;

import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import protobuf.EdaChatMessage.ChatMessageDelivery;
import protobuf.EdaChatMessage.ChatReactionDelivery;

/**
 * 수신자의 소켓은 그 유저가 붙은 채팅 서버 메모리에만 있으므로 "아무 채팅 서버" 가 아니라 정해진 한 대로 보내야 한다.
 * 그래서 로드밸런싱(name)을 쓰지 않고 호출할 때 첫 인자 {@link URI} 로 대상 서버를 정한다 (url 은 URI 로 덮어써지는 자리값).
 */
@FeignClient(name = "chat-server", url = "http://chat-server", configuration = ChatServerClientConfig.class)
public interface ChatServerClient {

    @PostMapping(value = "/internal/chat/deliver", consumes = "application/x-protobuf")
    void deliver(URI chatServerUri, ChatMessageDelivery delivery);

    /**
     * 이모지 리액션이 바뀐 메시지의 reactions 전체 (같은 타임아웃 / 재시도 설정)
     */
    @PostMapping(value = "/internal/chat/deliver-reaction", consumes = "application/x-protobuf")
    void deliverReaction(URI chatServerUri, ChatReactionDelivery delivery);
}
