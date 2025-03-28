package com.distributed.chat.system.mysql.repository.querydsl;

import static com.distributed.chat.system.mysql.entity.QTeam.team;

import com.distributed.chat.system.mysql.entity.Team;
import com.distributed.chat.system.mysql.repository.mapping.dto.GetTeamsMappingDto;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class TeamQueryDslImpl implements TeamQueryDsl {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Page<GetTeamsMappingDto> findTeamsQueryDsl(Map<String, Object> requestFilter, Pageable pageable) {
        List<GetTeamsMappingDto> content = jpaQueryFactory.select(Projections.fields(GetTeamsMappingDto.class,
                team.id.as("teamId"),
                team.teamName,
                team.deleteYn,
                team.createDt,
                team.updateDt,
                team.createUserId,
                team.updateUserId
            ))
            .from(team)
            .where(generateWhereConditionTeams(requestFilter))
            .orderBy(getOrderSpecifierTeams(pageable.getSort()).toArray(OrderSpecifier[]::new))
            .fetch();

        JPAQuery<Long> countQuery = jpaQueryFactory.select(team.count())
            .from(team)
            .where(generateWhereConditionTeams(requestFilter));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanBuilder generateWhereConditionTeams(Map<String, Object> filter) {
        if (filter == null) {
            return null;
        }

        BooleanBuilder builder = new BooleanBuilder();

        // 팀 ID 필터
        if (filter.get("teamId") != null) {
            Long teamId = (Long) filter.get("teamId");
            builder.and(team.id.eq(teamId));
        }

        // 팀 이름 필터
        if (!StringUtils.isBlank((String) filter.get("teamName"))
            && filter.get("teamName") != null) {
            String teamName = (String) filter.get("teamName");
            builder.and(team.teamName.contains(teamName.trim()));
        }

        // 삭제 여부 필터
        if (filter.get("deleteYn") != null) {
            Boolean deleteYn = (Boolean) filter.get("deleteYn");
            builder.and(team.deleteYn.eq(deleteYn));
        }

        return builder;
    }

    private List<OrderSpecifier> getOrderSpecifierTeams(Sort sort) {

        List<OrderSpecifier> orders = new ArrayList<>();

        sort.forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;

            // Sortable 구현체에서 getColumnName을 from절 QClass에서 찾음
            Path<Object> fieldPath = Expressions.path(Team.class, team, order.getProperty());

            orders.add(new OrderSpecifier(direction, fieldPath));
        });

        // 두번째 우선 정렬 (기본값)
        orders.add(new OrderSpecifier(Order.DESC, team.id));

        return orders;
    }
}
