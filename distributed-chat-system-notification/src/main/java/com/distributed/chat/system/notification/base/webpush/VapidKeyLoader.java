package com.distributed.chat.system.notification.base.webpush;

import com.distributed.chat.system.mysql.entity.VapidKey;
import com.distributed.chat.system.mysql.repository.VapidKeyRepository;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.BigIntegers;

/**
 * VAPID 키를 정한다. 키가 바뀌면 브라우저의 기존 구독이 전부 무효가 되므로 한 번 정한 키는 계속 쓴다
 * <ol>
 *   <li>환경변수 VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY 가 있으면 그 값 (둘 중 하나만 있으면 설정 실수라 뜨지 않는다)</li>
 *   <li>없으면 MySQL vapid_key (id = 1) 의 값</li>
 *   <li>그것도 없으면 (처음 뜰 때) 새로 만들어 insert ignore 로 넣고 다시 읽는다.
 *       여러 인스턴스가 동시에 처음 떠도 먼저 넣은 한 쌍만 남고, 모두 그 값을 쓴다</li>
 * </ol>
 */
@Slf4j
public class VapidKeyLoader {

    private final VapidKeyRepository vapidKeyRepository;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public VapidKeyLoader(VapidKeyRepository vapidKeyRepository, String publicKey, String privateKey, String subject) {
        this.vapidKeyRepository = vapidKeyRepository;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    public VapidKeys load() {
        boolean hasPublic = publicKey != null && !publicKey.isBlank();
        boolean hasPrivate = privateKey != null && !privateKey.isBlank();
        if (hasPublic || hasPrivate) {
            if (!(hasPublic && hasPrivate)) {
                throw new IllegalStateException("VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY must be set together");
            }
            log.info("VAPID key from environment, publicKey : {}", publicKey);
            return new VapidKeys(publicKey.strip(), privateKey.strip(), subject);
        }

        VapidKey stored = vapidKeyRepository.findById(VapidKey.SINGLE_ID).orElseGet(this::generateAndStore);
        return new VapidKeys(stored.getPublicKey(), stored.getPrivateKey(), subject);
    }

    /**
     * 같은 트랜잭션으로 묶지 않는다 : REPEATABLE READ 에서 처음 읽은 스냅샷에 묶이면 다른 인스턴스가 먼저 넣은 행을 못 본다
     */
    private VapidKey generateAndStore() {
        String[] generated = generate();
        int inserted = vapidKeyRepository.insertIfAbsent(VapidKey.SINGLE_ID, generated[0], generated[1]);
        VapidKey winner = vapidKeyRepository.findById(VapidKey.SINGLE_ID)
            .orElseThrow(() -> new IllegalStateException("vapid_key row missing after insert"));
        if (inserted == 1) {
            log.info("VAPID key generated and stored in vapid_key, publicKey : {}", winner.getPublicKey());
        } else {
            log.info("VAPID key stored by another instance first, using it, publicKey : {}", winner.getPublicKey());
        }
        return winner;
    }

    /**
     * @return [공개키, 개인키] base64url (패딩 없음). 공개키는 비압축 65바이트 = 브라우저 applicationServerKey 형식
     */
    static String[] generate() {
        BouncyCastle.register();
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(Utils.ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(ECNamedCurveTable.getParameterSpec(Utils.CURVE));
            KeyPair keyPair = generator.generateKeyPair();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return new String[]{
                encoder.encodeToString(Utils.encode((ECPublicKey) keyPair.getPublic())),
                // d 를 32바이트로 맞춘다 (BigInteger.toByteArray 는 33 / 31바이트가 될 수 있다. 다른 VAPID 도구와 같은 형식)
                encoder.encodeToString(BigIntegers.asUnsignedByteArray(32, ((ECPrivateKey) keyPair.getPrivate()).getD()))
            };
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID key generation failed", e);
        }
    }
}
