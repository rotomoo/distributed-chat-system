package com.distributed.chat.system.mysql.entity;

import com.distributed.chat.system.mysql.entity.base.BaseLoginInfoEntity;
import com.distributed.chat.system.mysql.pk.TeamUserPk;
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
@Table(name = "team_user", indexes = {
    @Index(name = "ix_team_user_delete_yn", columnList = "delete_yn")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@IdClass(TeamUserPk.class)
@DynamicInsert
@DynamicUpdate
public class TeamUser extends BaseLoginInfoEntity {

    @Id
    @Column(columnDefinition = "int(10) unsigned not null comment '팀 ID'")
    private Long teamId;

    @Id
    @Column(columnDefinition = "int(10) unsigned not null comment '회원 ID'")
    private Long userId;

    @Column(columnDefinition = "boolean not null default false comment '삭제 여부'")
    private Boolean deleteYn;
}
