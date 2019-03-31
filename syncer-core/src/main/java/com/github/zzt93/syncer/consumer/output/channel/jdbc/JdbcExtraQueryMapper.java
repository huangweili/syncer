package com.github.zzt93.syncer.consumer.output.channel.jdbc;

import com.github.zzt93.syncer.common.data.ExtraQuery;
import com.github.zzt93.syncer.common.expr.ParameterReplace;
import com.github.zzt93.syncer.consumer.output.channel.mapper.ExtraQueryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * @author zzt
 */
@Deprecated
public class JdbcExtraQueryMapper implements ExtraQueryMapper {

  private static final String SELECT_3_FROM_0_1_WHERE_ID_2 = "select ?3 from `?0`.`?1` where ?2";
  private final Logger logger = LoggerFactory.getLogger(JdbcExtraQueryMapper.class);
  private final JdbcTemplate template;

  public JdbcExtraQueryMapper(JdbcTemplate jdbcTemplate) {
    template = jdbcTemplate;
  }

  @Override
  public Map<String, Object> map(ExtraQuery extraQuery) {
    String[] target = extraQuery.getSelect();
    String select = Arrays.toString(extraQuery.getSelect());
    String sql = ParameterReplace.orderedParam(SELECT_3_FROM_0_1_WHERE_ID_2,
        extraQuery.getIndexName(), extraQuery.getTypeName(), extraQuery.getQueryBy().toString(),
        select);
    List<Map<String, Object>> maps = template.queryForList(sql);
    if (maps.size() > 1) {
      logger.warn("Multiple query results exists, only use the first");
    } else if (maps.size() == 0) {
      logger.warn("Fail to find any match by " + extraQuery);
      return Collections.emptyMap();
    }
    Map<String, Object> hit = maps.get(0);
    Map<String, Object> res = new HashMap<>();
    for (int i = 0; i < target.length; i++) {
      res.put(extraQuery.getAs(i), hit.get(target[i]));
    }
    return res;
  }

}
