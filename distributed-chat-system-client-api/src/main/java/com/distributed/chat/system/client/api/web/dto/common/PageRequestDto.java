package com.distributed.chat.system.client.api.web.dto.common;

import com.distributed.chat.system.common.util.StringUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageRequestDto {

    @Schema(name = "pageNumber", description = "페이지 번호. 기본값 1.", example = "1")
    protected Integer pageNumber = 1;

    @Schema(name = "pageSize", description = "페이지 당 항목 수. 기본값 10.", example = "10")
    protected Integer pageSize = 10;

    public Map<String, Object> toFilterMap() {
        return StringUtil.toMap(this);
    }

    public Pageable toPageable() {
        return PageRequest.of(
            pageNumber - 1,
            pageSize);
    }

    public Pageable toPageable(Sortable sort) {
        return PageRequest.of(
            pageNumber - 1,
            pageSize,
            Sort.by(sort.getDirection(), sort.getColumnName()));
    }
}
