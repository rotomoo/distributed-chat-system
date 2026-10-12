package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.ChannelUser;
import com.distributed.chat.system.mysql.pk.ChannelUserPk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelUserRepository extends JpaRepository<ChannelUser, ChannelUserPk> {

    List<ChannelUser> findAllByUserIdAndDeleteYnIsFalse(Long userId);

    List<ChannelUser> findAllByChannelIdAndDeleteYnIsFalse(Long channelId);

    List<ChannelUser> findAllByChannelIdInAndDeleteYnIsFalse(Collection<Long> channelIds);

    boolean existsByChannelIdAndUserIdAndDeleteYnIsFalse(Long channelId, Long userId);
}
