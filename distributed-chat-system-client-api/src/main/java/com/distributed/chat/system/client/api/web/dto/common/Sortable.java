package com.distributed.chat.system.client.api.web.dto.common;

import org.springframework.data.domain.Sort.Direction;

public interface Sortable {

    String getColumnName();

    Direction getDirection();
}
