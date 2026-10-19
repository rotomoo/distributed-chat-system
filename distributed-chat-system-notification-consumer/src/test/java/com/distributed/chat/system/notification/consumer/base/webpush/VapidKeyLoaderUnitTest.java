package com.distributed.chat.system.notification.consumer.base.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.distributed.chat.system.mysql.entity.VapidKey;
import com.distributed.chat.system.mysql.repository.VapidKeyRepository;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("[단위] VapidKeyLoader 테스트")
@ExtendWith(MockitoExtension.class)
class VapidKeyLoaderUnitTest {

    private static final String SUBJECT = "mailto:ops@example.com";

    @Mock
    private VapidKeyRepository vapidKeyRepository;

    @Test
    @DisplayName("success case : 환경변수에 공개키 / 개인키가 있으면 그 값을 쓰고 DB 는 보지 않는다")
    void 환경변수에_공개키_개인키가_있으면_그_값을_쓰고_DB_는_보지_않는다() {
        // arrange
        String[] env = VapidKeyLoader.generate();

        // act
        VapidKeys keys = new VapidKeyLoader(vapidKeyRepository, env[0], " " + env[1] + " ", SUBJECT).load();

        // assert
        assertThat(keys).isEqualTo(new VapidKeys(env[0], env[1], SUBJECT));
        verifyNoInteractions(vapidKeyRepository);
    }

    @Test
    @DisplayName("fail case : 공개키 / 개인키 중 하나만 있으면 설정 실수라 뜨지 않는다")
    void 공개키_개인키_중_하나만_있으면_뜨지_않는다() {
        // arrange
        String[] env = VapidKeyLoader.generate();

        // act
        Throwable onlyPublic = catchThrowable(() -> new VapidKeyLoader(vapidKeyRepository, env[0], "", SUBJECT).load());
        Throwable onlyPrivate = catchThrowable(() -> new VapidKeyLoader(vapidKeyRepository, null, env[1], SUBJECT).load());

        // assert
        assertThat(onlyPublic).isInstanceOf(IllegalStateException.class);
        assertThat(onlyPrivate).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(vapidKeyRepository);
    }

    @Test
    @DisplayName("success case : 환경변수가 없고 DB 에 저장된 키가 있으면 새로 만들지 않고 그 키를 쓴다")
    void 환경변수가_없고_DB_에_저장된_키가_있으면_그_키를_쓴다() {
        // arrange
        String[] stored = VapidKeyLoader.generate();
        when(vapidKeyRepository.findById(VapidKey.SINGLE_ID)).thenReturn(Optional.of(new VapidKey(1, stored[0], stored[1])));

        // act
        VapidKeys keys = new VapidKeyLoader(vapidKeyRepository, "", "", SUBJECT).load();

        // assert
        assertThat(keys).isEqualTo(new VapidKeys(stored[0], stored[1], SUBJECT));
        verify(vapidKeyRepository, never()).insertIfAbsent(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("success case : 처음 뜨면 키를 만들어 id = 1 로 저장하고 저장된 값을 쓴다")
    void 처음_뜨면_키를_만들어_id_1_로_저장하고_저장된_값을_쓴다() {
        // arrange : 처음엔 없고, 넣은 뒤 다시 읽으면 넣은 값
        ArgumentCaptor<String> publicKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> privateKey = ArgumentCaptor.forClass(String.class);
        when(vapidKeyRepository.insertIfAbsent(eq(VapidKey.SINGLE_ID), publicKey.capture(), privateKey.capture())).thenReturn(1);
        when(vapidKeyRepository.findById(VapidKey.SINGLE_ID)).thenReturn(Optional.empty())
            .thenAnswer(invocation -> Optional.of(new VapidKey(1, publicKey.getValue(), privateKey.getValue())));

        // act
        VapidKeys keys = new VapidKeyLoader(vapidKeyRepository, null, null, SUBJECT).load();

        // assert : 공개키 = 비압축 P-256 65바이트(0x04 시작), 개인키 = 32바이트, base64url 패딩 없음
        byte[] decodedPublic = Base64.getUrlDecoder().decode(keys.publicKey());
        assertThat(decodedPublic).hasSize(65);
        assertThat(decodedPublic[0]).isEqualTo((byte) 0x04);
        assertThat(Base64.getUrlDecoder().decode(keys.privateKey())).hasSize(32);
        assertThat(keys.publicKey()).doesNotContain("=", "+", "/");
        assertThat(keys.publicKey()).isEqualTo(publicKey.getValue());
        assertThat(keys.subject()).isEqualTo(SUBJECT);
        // 만든 키는 한 쌍이다 (라이브러리가 서명 준비할 때 확인)
        assertThatCode(() -> new WebPushRequestFactory(keys)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("success case : 동시에 처음 뜬 다른 인스턴스가 먼저 저장했으면 내가 만든 키를 버리고 저장된 키를 쓴다")
    void 다른_인스턴스가_먼저_저장했으면_내가_만든_키를_버리고_저장된_키를_쓴다() {
        // arrange : insert ignore 가 무시됨(0) → 다시 읽으면 다른 인스턴스의 키
        String[] winner = VapidKeyLoader.generate();
        ArgumentCaptor<String> mine = ArgumentCaptor.forClass(String.class);
        when(vapidKeyRepository.insertIfAbsent(eq(VapidKey.SINGLE_ID), mine.capture(), anyString())).thenReturn(0);
        when(vapidKeyRepository.findById(VapidKey.SINGLE_ID)).thenReturn(Optional.empty())
            .thenReturn(Optional.of(new VapidKey(1, winner[0], winner[1])));

        // act
        VapidKeys keys = new VapidKeyLoader(vapidKeyRepository, "", "", SUBJECT).load();

        // assert
        assertThat(keys.publicKey()).isEqualTo(winner[0]).isNotEqualTo(mine.getValue());
        assertThat(keys.privateKey()).isEqualTo(winner[1]);
    }

    @Test
    @DisplayName("fail case : 공개키와 개인키가 한 쌍이 아니면 서명 준비에서 실패한다 (뜰 때 바로 알 수 있게)")
    void 공개키와_개인키가_한_쌍이_아니면_서명_준비에서_실패한다() {
        // arrange
        String[] first = VapidKeyLoader.generate();
        String[] second = VapidKeyLoader.generate();

        // act
        Throwable thrown = catchThrowable(() -> new WebPushRequestFactory(new VapidKeys(first[0], second[1], SUBJECT)));

        // assert
        assertThat(thrown).isInstanceOf(java.security.InvalidKeyException.class).hasMessageContaining("do not match");
    }
}
