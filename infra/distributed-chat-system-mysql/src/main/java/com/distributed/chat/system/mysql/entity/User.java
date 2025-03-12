package com.distributed.chat.system.mysql.entity;

import com.distributed.chat.system.mysql.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(columnDefinition = "boolean not null default false comment '삭제 여부'")
    private Boolean deletedYn;
}
