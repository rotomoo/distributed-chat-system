package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.Channel;
import com.distributed.chat.system.mysql.enums.ChannelType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findAllByTeamIdAndChannelTypeAndDeleteYnIsFalseOrderByIdAsc(Long teamId, ChannelType channelType);

    List<Channel> findAllByIdInAndTeamIdAndChannelTypeAndDeleteYnIsFalse(Collection<Long> ids, Long teamId, ChannelType channelType);

    boolean existsByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(Long teamId, ChannelType channelType, String channelName);

    Optional<Channel> findByTeamIdAndChannelTypeAndChannelNameAndDeleteYnIsFalse(Long teamId, ChannelType channelType, String channelName);

    Optional<Channel> findByIdAndDeleteYnIsFalse(Long id);
}
