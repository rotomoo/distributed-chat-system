package com.distributed.chat.system.mysql.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseLoginInfoEntity extends BaseTimeEntity {

    @CreatedBy
    @Column(columnDefinition = "int(10) unsigned comment '생성 로그인 ID'")
    private Long createAdminId;

    @LastModifiedBy
    @Column(columnDefinition = "int(10) unsigned comment '수정 로그인 ID'")
    private Long updateAdminId;
}
