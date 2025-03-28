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
@Table(name = "team", indexes = {
    @Index(name = "ix_team_team_name", columnList = "teamName"),
    @Index(name = "ix_team_delete_yn", columnList = "delete_yn")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class Team extends BaseLoginInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "int(10) unsigned not null comment '팀 ID'")
    private Long id;

    @Column(columnDefinition = "varchar(50) not null comment '팀 이름'")
    private String teamName;

    @Column(columnDefinition = "boolean not null default false comment '삭제 여부'")
    private Boolean deleteYn;
}
