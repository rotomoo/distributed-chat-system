package com.distributed.chat.system.mysql.pk;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class TeamUserPk implements Serializable {

    private Long teamId;

    private Long userId;
}
