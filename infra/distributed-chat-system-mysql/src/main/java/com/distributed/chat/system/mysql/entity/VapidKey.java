package com.distributed.chat.system.mysql.entity;

import com.distributed.chat.system.mysql.entity.base.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 환경변수로 받지 않았을 때 알림 서버가 처음 뜰 때 만든 VAPID 키 (행은 id = {@link #SINGLE_ID} 하나뿐).
 * 키가 바뀌면 기존 구독이 전부 무효가 되므로 한 번 만들면 바꾸지 않는다. 쓰는 쪽은 notification-consumer 하나뿐이다
 */
@Entity
@Table(name = "vapid_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class VapidKey extends BaseTimeEntity {

    /**
     * PK 를 고정값으로 두어 여러 인스턴스가 동시에 처음 떠도 행이 하나만 생긴다 (먼저 넣은 쪽이 이김)
     */
    public static final int SINGLE_ID = 1;

    @Id
    @Column(columnDefinition = "int unsigned not null comment 'VAPID 키 ID (항상 1)'")
    private Integer id;

    @Column(columnDefinition = "varchar(255) not null comment '공개키 (base64url, P-256 비압축 65바이트)'")
    private String publicKey;

    @Column(columnDefinition = "varchar(255) not null comment '개인키 (base64url, 32바이트)'")
    private String privateKey;
}
