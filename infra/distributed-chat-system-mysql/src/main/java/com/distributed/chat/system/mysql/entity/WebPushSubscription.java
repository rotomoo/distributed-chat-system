package com.distributed.chat.system.mysql.entity;

import com.distributed.chat.system.mysql.entity.base.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 브라우저 웹 푸시 구독 (PushSubscription). 구독 / 해제는 client-api, notification-consumer 는 읽고 만료된 구독(404 / 410)만 지운다.
 * <p>
 * endpoint 는 브라우저(기기)마다 하나라서 고유하다. 같은 브라우저에서 다른 계정으로 로그인해 다시 구독하면 주인이 바뀐다 (upsert)
 */
@Entity
@Table(name = "web_push_subscription",
    uniqueConstraints = @UniqueConstraint(name = "ux_web_push_subscription_endpoint", columnNames = "endpoint"),
    indexes = @Index(name = "ix_web_push_subscription_user_id", columnList = "userId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class WebPushSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "bigint unsigned not null comment '구독 ID'")
    private Long id;

    @Column(columnDefinition = "int(10) unsigned not null comment '회원 ID'")
    private Long userId;

    /**
     * 푸시 서비스 URL. 유니크 인덱스 길이 제한(3072 bytes) 안에 들도록 ascii (URL 은 ascii 다).
     * 토큰이 대소문자를 구분하므로 ascii_bin (기본 ascii_general_ci 면 대소문자만 다른 두 기기가 한 행으로 합쳐진다)
     */
    @Column(columnDefinition = "varchar(1000) character set ascii collate ascii_bin not null comment '푸시 서비스 endpoint (대소문자 구분)'")
    private String endpoint;

    @Column(columnDefinition = "varchar(255) not null comment '브라우저 공개키 (base64url, P-256)'")
    private String p256dh;

    @Column(columnDefinition = "varchar(255) not null comment '인증 secret (base64url)'")
    private String auth;
}
