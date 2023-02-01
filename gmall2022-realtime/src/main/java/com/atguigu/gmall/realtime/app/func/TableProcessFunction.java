package com.atguigu.gmall.realtime.app.func;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.realtime.beans.TableProcess;
import com.atguigu.gmall.realtime.common.GmallConfig;
import com.atguigu.gmall.realtime.utils.PhoenixUtil;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;

import java.util.*;

/**
 * @author zhangxin
 * @Description dim处理主流、广播流数据、phoenix建表
 * @createTime 2023年01月30日 15:50:00
 */

public class TableProcessFunction extends BroadcastProcessFunction<JSONObject, String, JSONObject> {

    private MapStateDescriptor<String, TableProcess> mapStateDescriptor;

    public TableProcessFunction(MapStateDescriptor<String,TableProcess> mapStateDescriptor) {
        this.mapStateDescriptor = mapStateDescriptor;
    }

    //对主流数据进行处理
    @Override
    public void processElement(JSONObject jsonObj, ReadOnlyContext ctx, Collector<JSONObject> out) throws Exception {

        //获取当前处理的数据的表名
        String tableName = jsonObj.getString("table");
        //获取广播状态
        ReadOnlyBroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapStateDescriptor);
        TableProcess tableProcess = broadcastState.get(tableName);
        //如果根据表名从状态中获取到的配置对象不为空 说明当前处理的这条数据是维度数据
        if(tableProcess != null){
            //获取data属性内容   data中记录的就是业务数据库中维度表影响的记录 "data":{"tm_name":"cls","logo_url":"sdfs","id":12}
            JSONObject dataJsonObj = jsonObj.getJSONObject("data");

            //在向下游传递数据前，过滤掉不需要传递的属性，在过滤的时候，我们可以借助配置表中的sink_columns配置
            String sinkColumns = tableProcess.getSinkColumns();
            filterColumn(dataJsonObj,sinkColumns);

            //在向下游传递数据前，需要将输出目的地补全上
            String sinkTable = tableProcess.getSinkTable();
            dataJsonObj.put("sink_table",sinkTable);

            //将对象中data属性部分向下游传递
            out.collect(dataJsonObj);
        }
    }

    //过滤掉data中不需要向下游传递的属性
    private void filterColumn(JSONObject dataJsonObj, String sinkColumns) {
        String[] columnArr = sinkColumns.split(",");
        List<String> columnList = Arrays.asList(columnArr);
        Set<Map.Entry<String, Object>> entrySet = dataJsonObj.entrySet();
        entrySet.removeIf(entry->!columnList.contains(entry.getKey()));
    }

    //对广播流数据进行处理
    @Override
    public void processBroadcastElement(String jsonStr, Context ctx, Collector<JSONObject> out) throws Exception {
        //jsonStr是FlinkCDC从配置表中读取的json字符串，为了开发方便，需要将jsonStr转换为jsonObj
        JSONObject jsonObj = JSON.parseObject(jsonStr);

        //获取广播状态
        BroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapStateDescriptor);

        //获取对配置表的操作类型
        String op = jsonObj.getString("op");

        if ("d".equals(op)) {
            //如果是删除操作，那么将配置信息从广播状态中删除掉
            TableProcess beforeTableProcess = jsonObj.getObject("before", TableProcess.class);
            broadcastState.remove(beforeTableProcess.getSourceTable());
        } else {
            //如果是除了删除以外的其它操作，那么将配置信息放到广播状态中
            TableProcess afterTableProcess = jsonObj.getObject("after", TableProcess.class);
            //获取配置表中配置的业务数据库维度表表名
            String sourceTable = afterTableProcess.getSourceTable();
            //获取phoenix中维度表表名
            String sinkTable = afterTableProcess.getSinkTable();
            //获取表中字段
            String sinkColumns = afterTableProcess.getSinkColumns();
            //获取建表主键
            String sinkPk = afterTableProcess.getSinkPk();
            //获取建表扩展
            String sinkExtend = afterTableProcess.getSinkExtend();

            //提前在phoenix中创建维度表
            checkTable(sinkTable, sinkColumns, sinkPk, sinkExtend);

            //将业务数据库维度表名作为key，将读取的一条配置信息封装为TableProcess对象作为value，放到 广播状态中
            broadcastState.put(sourceTable, afterTableProcess);
        }

    }

    //提前在phoenix中创建维度表
    private void checkTable(String tableName, String sinkColumns, String pk, String ext) {
        if (pk == null) {
            pk = "id";
        }
        if (ext == null) {
            ext = "";
        }

        //获取建表字段并使用逗号对其进行分隔
        String[] columnArr = sinkColumns.split(",");
        //拼接建表语句
        StringBuilder createSql = new StringBuilder("create table if not exists " + GmallConfig.PHOENIX_SCHEMA + "." + tableName + "(");
        for (int i = 0; i < columnArr.length; i++) {
            String columnName = columnArr[i];
            //判断是否为主键字段
            if (columnName.equals(pk)) {
                createSql.append(columnName).append(" varchar primary key");
            } else {
                createSql.append(columnName).append(" varchar");
            }
            //除了最后一个字段外，其它字段在拼接后，都要加一个逗号分隔
            if (i < columnArr.length - 1) {
                createSql.append(",");
            }
        }
        createSql.append(")").append(ext);
        System.out.println("在phoenix中建表的语句为:" + createSql);

        PhoenixUtil.executeSql(createSql.toString());

    }
}
