package com.distributed.chat.system.mysql.repository.querydsl;

import com.distributed.chat.system.mysql.repository.mapping.dto.GetTeamsMappingDto;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TeamQueryDsl {

    Page<GetTeamsMappingDto> findTeamsQueryDsl(Map<String, Object> requestFilter, Pageable pageable);
}
