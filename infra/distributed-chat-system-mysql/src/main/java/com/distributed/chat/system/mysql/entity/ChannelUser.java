package com.distributed.chat.system.mysql.entity;

import com.distributed.chat.system.mysql.entity.base.BaseLoginInfoEntity;
import com.distributed.chat.system.mysql.pk.ChannelUserPk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "channel_user", indexes = {
    @Index(name = "ix_channel_user_delete_yn", columnList = "delete_yn")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@IdClass(ChannelUserPk.class)
@DynamicInsert
@DynamicUpdate
public class ChannelUser extends BaseLoginInfoEntity {

    @Id
    @Column(columnDefinition = "int(10) unsigned not null comment '채널 ID'")
    private Long channelId;

    @Id
    @Column(columnDefinition = "int(10) unsigned not null comment '회원 ID'")
    private Long userId;

    @Column(columnDefinition = "int(10) unsigned not null default 0 comment '안읽은 메시지 수'")
    private Integer unreadCnt;

    @Column(columnDefinition = "varchar(50) comment '마지막 메시지'")
    private String lastMessage;

    /**
     * 이 채널에서 마지막으로 읽은 메시지 (MongoDB chatMessage _id, 소문자 hex 24자리). 안 읽은 수 = 이보다 뒤의 남이 보낸 메시지.
     * 소문자 hex 고정 길이라 문자열 비교 순서가 ObjectId(시간) 순서와 같다
     */
    @Column(columnDefinition = "varchar(24) comment '마지막으로 읽은 메시지 ID'")
    private String lastReadMessageId;

    @Column(columnDefinition = "boolean not null default false comment '삭제 여부'")
    private Boolean deleteYn;
}
