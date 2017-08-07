package cn.sinobest.policeunion.biz.gxwj.graph.search.relation.impl;

import cn.sinobest.policeunion.biz.gxwj.graph.common.resource.GraphRelation;
import cn.sinobest.policeunion.biz.gxwj.graph.core.Graph;
import cn.sinobest.policeunion.biz.gxwj.graph.core.pj.GraphNode;
import cn.sinobest.policeunion.biz.gxwj.graph.core.pj.ValueNode;
import cn.sinobest.policeunion.biz.gxwj.graph.search.relation.IRelationService;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jodd.util.StringUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by zhouyi1 on 2016/6/27 0027.
 */
@Service(value = "gxwj.nodeRelationService")
public class NodeRelationService implements IRelationService {
    private static final Log logger = LogFactory.getLog(NodeRelationService.class);
    @Value("#{configProperties['maxSize']}")
    private Integer maxSize = Integer.MAX_VALUE;
    @Resource(name = "jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    public Map<String, ValueNode> getFromNodeMap(final StringBuilder condition, final GraphRelation relation, Set<ValueNode> fromNodes) {
        final String conditionPartStr = relation.getFromColumn() + " in (";

        condition.append(" and (");
        condition.append(conditionPartStr);
        final Integer[] count = {new Integer(maxSize)};
        Set set = Sets.newHashSet(fromNodes);

        final Map<String, ValueNode> fromNodeMaps = Maps.uniqueIndex(set, new Function<ValueNode, String>() {
            @Override
            public String apply(ValueNode node) {
                condition.append("'");
                condition.append(node.getValue());
                if (--count[0] == 0) {
                    condition.append(") ");
                    condition.append("or " + conditionPartStr);
                    count[0] = maxSize;
                } else {
                    condition.append("',");
                }
                return node.getValue();
            }
        });
        if (condition.lastIndexOf(",") == condition.length() - 1) {
            condition.deleteCharAt(condition.length() - 1);
            condition.append("))");
        } else {
            condition.delete(condition.length() - ("or " + conditionPartStr).length(), condition.length());
            condition.append(")");
        }

        return fromNodeMaps;
    }

    public String getNotSql(Graph graph, GraphRelation relation) {
        Set<ValueNode> nodeSet = graph.getValueNode(relation.getRelationName());
        if (nodeSet.isEmpty()) {
            return "";
        }
        String[] nodeValues = new String[nodeSet.size()];
        for (ValueNode node : nodeSet) {
            int i = 0;
            nodeValues[i++] = node.getValue();
        }
        String notSqlPart = StringUtil.join(nodeValues, ",");
        return String.format(" and %s not in (%s) ", relation.getFromColumn(), notSqlPart);
    }

    @Value("#{configProperties['limitSQL']}")
    private String limitSQL;

    public void search(final Graph graph, final Boolean detail, final GraphRelation relation, Set<ValueNode> startNodes) {
        final StringBuilder condition = new StringBuilder();
        final Map<String, ValueNode> fromNodeMaps = getFromNodeMap(condition, relation, startNodes);
        String notSql = getNotSql(graph, relation);

        StringBuilder conditionNoNeed = new StringBuilder();

        String sql = relation.getRelationSql() + condition.toString() + conditionNoNeed.toString();
        logger.trace("relation:" + relation.getFromType() + "-" + relation.getToType());
        logger.trace("dig sql:" + sql + notSql + " " + limitSQL);

        try {

            jdbcTemplate.query(sql + notSql + " " + limitSQL, new ColumnMapRowMapper() {
                @Override
                public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Map<String, Object> maps = super.mapRow(rs, rowNum);
                    List<String> toNodeValues = getNodeValues(maps, relation.getToColumn());

                    if (!toNodeValues.isEmpty() && toNodeValues.size() > 0) {
                        List<String> toNodePkValues = getNodeValues(maps, relation.getToPKColumn());
                        Iterator<String> iteratorToNodePkValues = toNodePkValues.iterator();
                        for (String toNodeValue : toNodeValues) {
                            //如果为空则用value，否则按照顺序获得pkvalue，假如pkvalue不够则一直使用第一个
                            String toNodePkValue = toNodePkValues.isEmpty()?toNodeValue:(iteratorToNodePkValues.hasNext() ? iteratorToNodePkValues.next() : toNodePkValues.get(0));

                            GraphNode nodeTo = new GraphNode(toNodeValue,toNodePkValue);
                            nodeTo.setType(relation.getToType().getType());
                            if (detail) {
                                nodeTo.setDetails(maps);
                            }
                            String fromNodeValue = maps.get(relation.getFromColumn()) == null ? null : maps.get(relation.getFromColumn()).toString();
                            String fromNodePkValue = maps.get(relation.getFromPKColumn()) == null ? "" : maps.get(relation.getFromPKColumn()).toString();
                            GraphNode nodeFrom = new GraphNode(fromNodeValue,fromNodePkValue,relation.getFromType().getType());
                            nodeTo.setType(relation.getToType().getType());
                            graph.addEdge(nodeFrom, nodeTo, relation.getRelationPk());
                        }
                    }
                    return maps;
                }
            });
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
        }
    }

    private List<String> getNodeValues(Map<String, Object> maps, String column) {
        logger.trace("column:" + column);
        List<String> nodeValues = new ArrayList<String>();
        for (String columnName : column.split(",")) {
            String columnValue = maps.get(columnName) == null ? null : maps.get(columnName).toString();
            if (columnValue != null) {
                nodeValues.add(columnValue);
            }
        }
        return nodeValues;
    }
}
