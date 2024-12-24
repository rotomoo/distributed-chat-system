package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.User;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("[통합] userRepository 테스트")
@SpringBootTest
@Transactional
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("true case: findByAccount 테스트")
    void findByAccount() {
        // given
        User user = User.builder()
            .userName("1")
            .account("findByAccountTest")
            .password("2")
            .deletedYn(false)
            .build();
        userRepository.save(user);

        // when
        Optional<User> foundUser = userRepository.findByAccount("findByAccountTest");

        // then
        Assertions.assertTrue(foundUser.isPresent());
        Assertions.assertEquals("findByAccountTest", foundUser.get().getAccount());
    }
}