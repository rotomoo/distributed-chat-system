package com.distributed.chat.system.client.api.web.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PagingInfo {

    @Schema(title = "전체 항목 수", name = "totalCount")
    private Long totalCount = 0l;

    @Schema(title = "전체 페이지 수", name = "totalPageCount")
    private Integer totalPageCount = 0;

    @Schema(title = "페이지 번호 (default: 1)", name = "pageNumber")
    private Integer pageNumber = 1;

    @Schema(title = "페이지 당 수 (default: 10)", name = "pageSize")
    private Integer pageSize = 10;

    public static PagingInfo createPagingInfo(Page<?> pageData) {
        return new PagingInfo(pageData.getTotalElements(), pageData.getTotalPages(),
            pageData.getNumber() + 1, pageData.getSize());
    }
}
