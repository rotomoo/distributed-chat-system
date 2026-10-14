package com.distributed.chat.system.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.common.event.ChatNotificationEvent;
import com.distributed.chat.system.mysql.entity.Channel;
import com.distributed.chat.system.mysql.entity.User;
import com.distributed.chat.system.mysql.entity.WebPushSubscription;
import com.distributed.chat.system.mysql.enums.ChannelType;
import com.distributed.chat.system.mysql.repository.ChannelRepository;
import com.distributed.chat.system.mysql.repository.ChannelUserRepository;
import com.distributed.chat.system.mysql.repository.UserRepository;
import com.distributed.chat.system.mysql.repository.WebPushSubscriptionRepository;
import com.distributed.chat.system.notification.base.webpush.WebPushSender;
import com.distributed.chat.system.notification.service.NotificationService.PushResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("[단위] NotificationService 테스트")
@ExtendWith(MockitoExtension.class)
class NotificationServiceUnitTest {

    private static final long CHANNEL_ID = 7L;
    private static final long SENDER = 1L;
    private static final long ALICE = 2L;
    private static final long BOB = 3L;
    private static final long CAROL = 4L;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ChannelUserRepository channelUserRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Mock
    private NotificationThrottle notificationThrottle;

    @Mock
    private WebPushSender webPushSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationService notificationService;

    @BeforeEach
    void setup() {
        notificationService = new NotificationService(channelRepository, channelUserRepository, userRepository,
            webPushSubscriptionRepository, notificationThrottle, webPushSender, objectMapper);
        lenient().when(userRepository.findById(SENDER)).thenReturn(Optional.of(user(SENDER, "철수", "chulsoo")));
        // 기본 : 30초 안에 보낸 적 없음 (넘긴 유저 모두 통과)
        lenient().when(notificationThrottle.acquire(anyCollection(), anyLong()))
            .thenAnswer(invocation -> new LinkedHashSet<>(invocation.<java.util.Collection<Long>>getArgument(0)));
        lenient().when(webPushSender.send(any(), any(), anyString())).thenReturn(CompletableFuture.completedFuture(201));
    }

    @Test
    @DisplayName("success case : 공개 채널이면 제목은 #채널명, 본문은 보낸사람: 미리보기 로 받는 사람의 구독 전부에 보낸다")
    void 공개_채널이면_제목은_채널명_본문은_보낸사람_미리보기_로_구독_전부에_보낸다() throws Exception {
        // arrange
        givenChannel(ChannelType.PUBLIC, "점심메뉴추천");
        givenUnmuted(List.of(ALICE, BOB), List.of(ALICE, BOB));
        WebPushSubscription alicePc = subscription(ALICE, "https://fcm.googleapis.com/a-pc");
        WebPushSubscription alicePhone = subscription(ALICE, "https://fcm.googleapis.com/a-phone");
        WebPushSubscription bobPc = subscription(BOB, "https://fcm.googleapis.com/b-pc");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE, BOB))).thenReturn(List.of(alicePc, alicePhone, bobPc));

        // act
        List<PushResult> results = notificationService.notify(notification(List.of(ALICE, BOB), "점심 뭐 먹을까요?", 0));

        // assert
        assertThat(results).hasSize(3).allMatch(PushResult::succeeded);
        WebPushPayload payload = sentPayload(alicePc);
        assertThat(payload.title()).isEqualTo("#점심메뉴추천");
        assertThat(payload.body()).isEqualTo("철수: 점심 뭐 먹을까요?");
        assertThat(payload.url()).isEqualTo("/?channel=7");
        assertThat(payload.tag()).isEqualTo("channel-7");
        verify(webPushSender).send(eq(alicePhone), any(), eq("channel-7"));
        verify(webPushSender).send(eq(bobPc), any(), eq("channel-7"));
    }

    @Test
    @DisplayName("success case : DM 이면 제목은 보낸 사람 이름이다")
    void DM_이면_제목은_보낸_사람_이름이다() throws Exception {
        // arrange
        givenChannel(ChannelType.DM, "dm:1:2");
        givenUnmuted(List.of(ALICE), List.of(ALICE));
        WebPushSubscription alicePc = subscription(ALICE, "https://fcm.googleapis.com/a-pc");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE))).thenReturn(List.of(alicePc));

        // act
        notificationService.notify(notification(List.of(ALICE), "안녕", 0));

        // assert
        WebPushPayload payload = sentPayload(alicePc);
        assertThat(payload.title()).isEqualTo("철수");
        assertThat(payload.body()).isEqualTo("철수: 안녕");
    }

    @Test
    @DisplayName("success case : 본문 없이 첨부만 보냈으면 본문은 보낸사람: 파일 N개 다")
    void 본문_없이_첨부만_보냈으면_본문은_파일_N개_다() throws Exception {
        // arrange
        givenChannel(ChannelType.PUBLIC, "general");
        givenUnmuted(List.of(ALICE), List.of(ALICE));
        WebPushSubscription alicePc = subscription(ALICE, "https://fcm.googleapis.com/a-pc");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE))).thenReturn(List.of(alicePc));

        // act
        notificationService.notify(notification(List.of(ALICE), "", 2));

        // assert
        assertThat(sentPayload(alicePc).body()).isEqualTo("철수: 파일 2개");
    }

    @Test
    @DisplayName("success case : 본문이 있으면 첨부가 있어도 미리보기를 쓴다")
    void 본문이_있으면_첨부가_있어도_미리보기를_쓴다() throws Exception {
        // arrange
        givenChannel(ChannelType.PUBLIC, "general");
        givenUnmuted(List.of(ALICE), List.of(ALICE));
        WebPushSubscription alicePc = subscription(ALICE, "https://fcm.googleapis.com/a-pc");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE))).thenReturn(List.of(alicePc));

        // act
        notificationService.notify(notification(List.of(ALICE), "사진 봐", 1));

        // assert
        assertThat(sentPayload(alicePc).body()).isEqualTo("철수: 사진 봐");
    }

    @Test
    @DisplayName("success case : 이름이 없으면 계정, 보낸 사람이 없으면 알 수 없는 사용자 로 쓴다")
    void 이름이_없으면_계정_보낸_사람이_없으면_알_수_없는_사용자_로_쓴다() throws Exception {
        // arrange
        givenChannel(ChannelType.DM, "dm:1:2");
        givenUnmuted(List.of(ALICE), List.of(ALICE));
        WebPushSubscription alicePc = subscription(ALICE, "https://fcm.googleapis.com/a-pc");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE))).thenReturn(List.of(alicePc));
        when(userRepository.findById(SENDER)).thenReturn(Optional.of(user(SENDER, null, "chulsoo")), Optional.empty());

        // act
        notificationService.notify(notification(List.of(ALICE), "a", 0));
        notificationService.notify(notification(List.of(ALICE), "b", 0));

        // assert
        List<WebPushPayload> payloads = sentPayloads(alicePc, 2);
        assertThat(payloads.get(0).title()).isEqualTo("chulsoo");
        assertThat(payloads.get(1).title()).isEqualTo("알 수 없는 사용자");
        assertThat(payloads.get(1).body()).isEqualTo("알 수 없는 사용자: b");
    }

    @Test
    @DisplayName("success case : 채널을 음소거한 사람(또는 이미 나간 사람)은 구독을 찾지도 보내지도 않는다")
    void 채널을_음소거한_사람은_구독을_찾지도_보내지도_않는다() {
        // arrange : BOB 는 음소거
        givenChannel(ChannelType.PUBLIC, "general");
        givenUnmuted(List.of(ALICE, BOB), List.of(ALICE));
        WebPushSubscription alicePc = subscription(ALICE, "https://fcm.googleapis.com/a-pc");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE))).thenReturn(List.of(alicePc));

        // act
        List<PushResult> results = notificationService.notify(notification(List.of(ALICE, BOB), "hi", 0));

        // assert
        assertThat(results).extracting(result -> result.subscription().getUserId()).containsExactly(ALICE);
        verify(webPushSubscriptionRepository).findAllByUserIdIn(List.of(ALICE));
    }

    @Test
    @DisplayName("success case : 모두 음소거했으면 구독 조회 / 묶기 / 발송을 하지 않는다")
    void 모두_음소거했으면_구독_조회_묶기_발송을_하지_않는다() {
        // arrange
        givenChannel(ChannelType.PUBLIC, "general");
        givenUnmuted(List.of(ALICE, BOB), List.of());

        // act
        List<PushResult> results = notificationService.notify(notification(List.of(ALICE, BOB), "hi", 0));

        // assert
        assertThat(results).isEmpty();
        verifyNoInteractions(webPushSubscriptionRepository, notificationThrottle, webPushSender);
    }

    @Test
    @DisplayName("success case : 30초 안에 같은 채널 알림을 받은 사람은 건너뛴다")
    void 삼십초_안에_같은_채널_알림을_받은_사람은_건너뛴다() {
        // arrange : ALICE 는 30초 안에 이미 받음
        givenChannel(ChannelType.PUBLIC, "general");
        givenUnmuted(List.of(ALICE, BOB), List.of(ALICE, BOB));
        WebPushSubscription alicePc = subscription(ALICE, "https://fcm.googleapis.com/a-pc");
        WebPushSubscription bobPc = subscription(BOB, "https://fcm.googleapis.com/b-pc");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE, BOB))).thenReturn(List.of(alicePc, bobPc));
        when(notificationThrottle.acquire(List.of(ALICE, BOB), CHANNEL_ID)).thenReturn(Set.of(BOB));

        // act
        notificationService.notify(notification(List.of(ALICE, BOB), "hi", 0));

        // assert
        verify(webPushSender, never()).send(eq(alicePc), any(), anyString());
        verify(webPushSender).send(eq(bobPc), any(), anyString());
    }

    @Test
    @DisplayName("success case : 구독이 없는 사람은 묶기 키(Redis)를 잡지 않는다")
    void 구독이_없는_사람은_묶기_키를_잡지_않는다() {
        // arrange : CAROL 은 구독 없음
        givenChannel(ChannelType.PUBLIC, "general");
        givenUnmuted(List.of(ALICE, CAROL), List.of(ALICE, CAROL));
        WebPushSubscription alicePc = subscription(ALICE, "https://fcm.googleapis.com/a-pc");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE, CAROL))).thenReturn(List.of(alicePc));

        // act
        notificationService.notify(notification(List.of(ALICE, CAROL), "hi", 0));

        // assert
        verify(notificationThrottle).acquire(List.of(ALICE), CHANNEL_ID);
    }

    @Test
    @DisplayName("success case : 푸시 서비스가 404 / 410 을 주면 그 구독을 지우고 나머지는 그대로 보낸다")
    void 푸시_서비스가_404_410_을_주면_그_구독을_지운다() {
        // arrange
        givenChannel(ChannelType.PUBLIC, "general");
        givenUnmuted(List.of(ALICE, BOB, CAROL), List.of(ALICE, BOB, CAROL));
        WebPushSubscription gone = subscription(ALICE, "https://fcm.googleapis.com/gone");
        WebPushSubscription notFound = subscription(BOB, "https://fcm.googleapis.com/not-found");
        WebPushSubscription ok = subscription(CAROL, "https://fcm.googleapis.com/ok");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE, BOB, CAROL))).thenReturn(List.of(gone, notFound, ok));
        when(webPushSender.send(eq(gone), any(), anyString())).thenReturn(CompletableFuture.completedFuture(410));
        when(webPushSender.send(eq(notFound), any(), anyString())).thenReturn(CompletableFuture.completedFuture(404));

        // act
        List<PushResult> results = notificationService.notify(notification(List.of(ALICE, BOB, CAROL), "hi", 0));

        // assert
        verify(webPushSubscriptionRepository).deleteByEndpoint("https://fcm.googleapis.com/gone");
        verify(webPushSubscriptionRepository).deleteByEndpoint("https://fcm.googleapis.com/not-found");
        verify(webPushSubscriptionRepository, never()).deleteByEndpoint("https://fcm.googleapis.com/ok");
        assertThat(results).filteredOn(PushResult::succeeded).extracting(PushResult::subscription).containsExactly(ok);
    }

    @Test
    @DisplayName("fail case : 429 / 500 / 연결 실패는 구독을 지우지 않고 예외도 던지지 않는다 (이번 알림만 버림)")
    void 일시적_오류는_구독을_지우지_않고_예외도_던지지_않는다() {
        // arrange
        givenChannel(ChannelType.PUBLIC, "general");
        givenUnmuted(List.of(ALICE, BOB, CAROL), List.of(ALICE, BOB, CAROL));
        WebPushSubscription tooMany = subscription(ALICE, "https://fcm.googleapis.com/429");
        WebPushSubscription serverError = subscription(BOB, "https://fcm.googleapis.com/500");
        WebPushSubscription refused = subscription(CAROL, "https://fcm.googleapis.com/refused");
        when(webPushSubscriptionRepository.findAllByUserIdIn(List.of(ALICE, BOB, CAROL))).thenReturn(List.of(tooMany, serverError, refused));
        when(webPushSender.send(eq(tooMany), any(), anyString())).thenReturn(CompletableFuture.completedFuture(429));
        when(webPushSender.send(eq(serverError), any(), anyString())).thenReturn(CompletableFuture.completedFuture(500));
        when(webPushSender.send(eq(refused), any(), anyString())).thenReturn(CompletableFuture.failedFuture(new ConnectException("refused")));

        // act
        List<PushResult> results = notificationService.notify(notification(List.of(ALICE, BOB, CAROL), "hi", 0));

        // assert
        assertThat(results).hasSize(3).noneMatch(PushResult::succeeded).noneMatch(PushResult::expired);
        assertThat(results.get(2).error()).isInstanceOf(ConnectException.class);
        verify(webPushSubscriptionRepository, never()).deleteByEndpoint(any());
    }

    @Test
    @DisplayName("fail case : 채널이 없거나 지워졌으면 아무것도 하지 않는다")
    void 채널이_없거나_지워졌으면_아무것도_하지_않는다() {
        // arrange
        when(channelRepository.findByIdAndDeleteYnIsFalse(CHANNEL_ID)).thenReturn(Optional.empty());

        // act
        List<PushResult> results = notificationService.notify(notification(List.of(ALICE), "hi", 0));

        // assert
        assertThat(results).isEmpty();
        verifyNoInteractions(channelUserRepository, webPushSubscriptionRepository, notificationThrottle, webPushSender);
    }

    @Test
    @DisplayName("fail case : 음소거 / 구독 조회(MySQL)가 실패하면 예외를 던진다 (재시도 토픽으로)")
    void 조회가_실패하면_예외를_던진다() {
        // arrange
        givenChannel(ChannelType.PUBLIC, "general");
        when(channelUserRepository.findUnmutedMemberUserIds(eq(CHANNEL_ID), any())).thenThrow(new IllegalStateException("mysql down"));

        // act
        Throwable thrown = catchThrowable(() -> notificationService.notify(notification(List.of(ALICE), "hi", 0)));

        // assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(webPushSender);
    }

    @Test
    @DisplayName("success case : 받는 사람이 없으면 아무것도 조회하지 않는다")
    void 받는_사람이_없으면_아무것도_조회하지_않는다() {
        // act
        List<PushResult> results = notificationService.notify(notification(List.of(), "hi", 0));

        // assert
        assertThat(results).isEmpty();
        verifyNoInteractions(channelRepository, channelUserRepository, webPushSubscriptionRepository, notificationThrottle, webPushSender);
    }

    private void givenChannel(ChannelType type, String name) {
        when(channelRepository.findByIdAndDeleteYnIsFalse(CHANNEL_ID))
            .thenReturn(Optional.of(Channel.builder().id(CHANNEL_ID).teamId(1L).channelName(name).channelType(type).deleteYn(false).build()));
    }

    private void givenUnmuted(List<Long> receivers, List<Long> unmuted) {
        when(channelUserRepository.findUnmutedMemberUserIds(CHANNEL_ID, receivers)).thenReturn(unmuted);
    }

    private static ChatNotificationEvent notification(List<Long> receivers, String preview, int attachments) {
        return new ChatNotificationEvent("6acc66675f99715e286f0f70", CHANNEL_ID, SENDER, preview, attachments, 1L, receivers);
    }

    private static WebPushSubscription subscription(Long userId, String endpoint) {
        return WebPushSubscription.builder().userId(userId).endpoint(endpoint).p256dh("p").auth("a").build();
    }

    private static User user(Long id, String name, String account) {
        return User.builder().id(id).userName(name).account(account).password("x").build();
    }

    private WebPushPayload sentPayload(WebPushSubscription subscription) throws Exception {
        return sentPayloads(subscription, 1).get(0);
    }

    private List<WebPushPayload> sentPayloads(WebPushSubscription subscription, int times) throws Exception {
        ArgumentCaptor<byte[]> payloads = ArgumentCaptor.forClass(byte[].class);
        verify(webPushSender, org.mockito.Mockito.times(times)).send(eq(subscription), payloads.capture(), eq("channel-7"));
        List<WebPushPayload> parsed = new java.util.ArrayList<>();
        for (byte[] payload : payloads.getAllValues()) {
            parsed.add(objectMapper.readValue(payload, WebPushPayload.class));
        }
        return parsed;
    }
}
