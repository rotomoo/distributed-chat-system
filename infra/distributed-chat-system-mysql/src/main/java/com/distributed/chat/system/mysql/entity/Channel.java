package com.distributed.chat.system.mysql.entity;

import com.distributed.chat.system.mysql.entity.base.BaseLoginInfoEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "channel", indexes = {
    @Index(name = "ix_channel_team_id", columnList = "teamId"),
    @Index(name = "ix_channel_channel_name", columnList = "channelName"),
    @Index(name = "ix_channel_delete_yn", columnList = "delete_yn")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class Channel extends BaseLoginInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "int(10) unsigned not null comment '채널 ID'")
    private Long id;

    @Column(columnDefinition = "int(10) unsigned comment '팀 ID'")
    private Long teamId;

    @Column(columnDefinition = "varchar(50) not null comment '채널 이름'")
    private String channelName;

    @Column(columnDefinition = "boolean not null default false comment '삭제 여부'")
    private Boolean deleteYn;
}
