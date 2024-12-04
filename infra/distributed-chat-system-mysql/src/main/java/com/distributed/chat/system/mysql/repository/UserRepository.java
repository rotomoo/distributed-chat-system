package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAccount(String account);

    boolean existsByAccount(String account);
}
