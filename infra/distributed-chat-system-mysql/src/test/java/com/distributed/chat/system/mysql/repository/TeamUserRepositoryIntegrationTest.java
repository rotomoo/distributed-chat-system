package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.TeamUser;
import com.distributed.chat.system.mysql.pk.TeamUserPk;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("[통합] TeamUserRepository 테스트")
@SpringBootTest
@Transactional
public class TeamUserRepositoryIntegrationTest {

    @Autowired
    private TeamUserRepository teamUserRepository;

    @Test
    @DisplayName("true case: 복합키 findById 테스트")
    void findById() {
        // given
        TeamUser teamUser = TeamUser.builder()
            .teamId(1L)
            .userId(2L)
            .deleteYn(false)
            .build();
        teamUserRepository.save(teamUser);

        // when
        Optional<TeamUser> foundTeamUser = teamUserRepository.findById(new TeamUserPk(1L, 2L));

        // then
        Assertions.assertTrue(foundTeamUser.isPresent());
        Assertions.assertEquals(1L, foundTeamUser.get().getTeamId());
    }

    @Test
    @DisplayName("true case: 복합키 findAllByTeamId 테스트")
    void findAllByTeamId() {
        // given
        TeamUser teamUser = TeamUser.builder()
            .teamId(1L)
            .userId(2L)
            .deleteYn(false)
            .build();
        teamUserRepository.save(teamUser);

        // when
        List<TeamUser> foundTeamUsers = teamUserRepository.findAllByTeamId(1L);

        // then
        Assertions.assertTrue(!foundTeamUsers.isEmpty());
        Assertions.assertEquals(2L, foundTeamUsers.get(0).getUserId());
    }
}
