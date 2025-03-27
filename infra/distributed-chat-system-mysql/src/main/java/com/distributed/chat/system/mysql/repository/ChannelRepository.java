package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

}
