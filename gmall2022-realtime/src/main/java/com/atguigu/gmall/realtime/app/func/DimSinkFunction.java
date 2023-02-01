package com.atguigu.gmall.realtime.app.func;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.realtime.common.GmallConfig;
import com.atguigu.gmall.realtime.utils.PhoenixUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.util.Collection;
import java.util.Set;

public class DimSinkFunction implements SinkFunction<JSONObject> {

    @Override
    public void invoke(JSONObject jsonObj, Context context) throws Exception {
        //将流中的json对象，拼接成upsert语句，写到phoenix表中
        // jsonObj: {"tm_name":"xzls","sink_table":"dim_base_trademark","id":12}
        // upsert语句: upsert into 表空间.表名(a,b,c,d) values(aa,bb,cc,dd)

        //获取输出的目的地
        String sinkTable = jsonObj.getString("sink_table");
        // {"tm_name":"xzls","id":12}
        jsonObj.remove("sink_table");

        //获取所有属性的名称
        Set<String> keys = jsonObj.keySet();
        //获取所有属性的值
        Collection<Object> values = jsonObj.values();

        String upsertSql = "upsert into " + GmallConfig.PHOENIX_SCHEMA + "." + sinkTable
                + "(" + StringUtils.join(keys, ",") + ") values('" + StringUtils.join(values, "','") + "')";

        System.out.println("向phoenix表中插入数据的SQL:" + upsertSql);

        PhoenixUtil.executeSql(upsertSql);
    }
}
