package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.QUser;
import com.distributed.chat.system.mysql.entity.User;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("[통합] mysql 의존성 테스트")
@SpringBootTest
@Transactional
class MysqlDependencyIntegrationTest {

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("true case : querydsl 테스트")
    void querydslTest() {
        // given
        User user = User.builder()
            .userName("querydslTest")
            .account("querydslTest")
            .password("querydslTest")
            .deletedYn(false)
            .build();
        em.persist(user);

        JPAQueryFactory query = new JPAQueryFactory(em);

        QUser qUser = QUser.user;

        // when
        User result = query
            .selectFrom(qUser)
            .where(qUser.userName.eq("querydslTest"))
            .fetchOne();

        // then
        Assertions.assertThat(result).isEqualTo(user);
        Assertions.assertThat(result.getId()).isEqualTo(user.getId());
    }
}
