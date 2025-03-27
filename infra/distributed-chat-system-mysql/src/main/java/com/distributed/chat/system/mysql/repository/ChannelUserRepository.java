package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.pk.ChannelUserPk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelUserRepository extends JpaRepository<ChannelUser, ChannelUserPk> {

}
