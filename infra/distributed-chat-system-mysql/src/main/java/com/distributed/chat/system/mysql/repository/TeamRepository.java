package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.Team;
import com.distributed.chat.system.mysql.repository.querydsl.TeamQueryDsl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long>, TeamQueryDsl {

    boolean existsByTeamNameAndDeleteYnIsFalse(String teamName);
}
