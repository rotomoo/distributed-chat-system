package com.distributed.chat.system.mysql.pk;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ChannelUserPk implements Serializable {

    private Long channelId;

    private Long userId;
}
