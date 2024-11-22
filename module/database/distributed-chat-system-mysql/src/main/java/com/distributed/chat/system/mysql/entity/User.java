package com.distributed.chat.system.mysql.entity;

import com.distributed.chat.system.mysql.entity.base.BaseTimeEntity;
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

@Entity
@Table(name = "user", indexes = {
    @Index(name = "ix_user_user_name", columnList = "userName")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "int(10) unsigned not null comment '회원 ID'")
    private Long id;

    @Column(columnDefinition = "varchar(50) comment '회원 이름'")
    private String userName;

    @Column(columnDefinition = "varchar(50) not null UNIQUE comment '계정'")
    private String account;

    @Column(columnDefinition = "varchar(255) not null comment '비밀번호'")
    private String password;

    @Column(columnDefinition = "varchar(50) not null comment '비밀번호 salt'")
    private String salt;

    @Column(columnDefinition = "boolean not null default false comment '삭제 여부'")
    private Boolean deletedYn;
}
