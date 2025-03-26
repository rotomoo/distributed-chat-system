package com.distributed.chat.system.mysql.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(columnDefinition = "datetime not null default CURRENT_TIMESTAMP comment '생성 일시'")
    private Instant createDt;

    @LastModifiedDate
    @Column(columnDefinition = "datetime not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '수정 일시'")
    private Instant updateDt;
}
