package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.TeamUser;
import com.distributed.chat.system.mysql.pk.TeamUserPk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamUserRepository extends JpaRepository<TeamUser, TeamUserPk> {

    List<TeamUser> findAllByTeamId(Long teamId);
}
