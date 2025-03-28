package com.distributed.chat.system.client.api.web.dto.team;

import com.distributed.chat.system.client.api.web.dto.common.Sortable;
import lombok.Getter;
import org.springframework.data.domain.Sort.Direction;

@Getter
public enum TeamsSort implements Sortable {
    ID_ASC("id", Direction.ASC),
    ID_DESC("id", Direction.DESC);

    private String columnName;
    private Direction direction;

    TeamsSort(String columnName, Direction direction) {
        this.columnName = columnName;
        this.direction = direction;
    }
}
