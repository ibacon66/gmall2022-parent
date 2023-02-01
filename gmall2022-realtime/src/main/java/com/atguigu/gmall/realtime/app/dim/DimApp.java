package com.atguigu.gmall.realtime.app.dim;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.realtime.app.func.DimSinkFunction;
import com.atguigu.gmall.realtime.app.func.TableProcessFunction;
import com.atguigu.gmall.realtime.beans.TableProcess;
import com.atguigu.gmall.realtime.utils.MyKafkaUtil;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.streaming.api.datastream.BroadcastConnectedStream;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;

/**
 * @author zhangxin
 * @Description 处理维度数据
 * @createTime 2023年01月30日 15:50:00
 */
public class DimApp {
    public static void main(String[] args) throws Exception {
        //  1.基本环境准备
        //1.1 指定流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //1.2 设置并行度
        env.setParallelism(4);

        /*
        //  2.检查点相关的设置
        //2.1 开启检查点
        env.enableCheckpointing(5000L, CheckpointingMode.EXACTLY_ONCE);
        //2.2 设置检查点超时时间
        env.getCheckpointConfig().setCheckpointTimeout(60000L);
        //2.3 设置job取消之后，检查点是否保留
        env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        //2.4 设置两个检查点之间最小时间间隔
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(2000L);
        //2.5 设置重启策略
        // env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3,3000L));
        env.setRestartStrategy(RestartStrategies.failureRateRestart(3, Time.days(30), Time.seconds(3)));
        //2.6 设置状态后端
        env.setStateBackend(new HashMapStateBackend());
        // env.getCheckpointConfig().setCheckpointStorage(new JobManagerCheckpointStorage());
        env.getCheckpointConfig().setCheckpointStorage("hdfs://hadoop102:8020/gmall/ck");
        //2.7 设置操作hadoop的用户
        System.setProperty("HADOOP_USER_NAME","atguigu");
        */

        // 3.从topic_db主题中读取数据

        //3.1 声明消费的主题以及消费者组
        String topic = "topic_db";
        String groupId = "dim_app_group";
        //3.2 创建消费者对象
        FlinkKafkaConsumer<String> kafkaConsumer = MyKafkaUtil.getKafkaConsumer(topic, groupId);
        //3.3 消费数据 封装为流
        DataStreamSource<String> kafkaStrDS = env.addSource(kafkaConsumer);


        // 4.对读取的数据进行简单的ETL操作

        SingleOutputStreamOperator<JSONObject> jsonObjDS = kafkaStrDS.process(new ProcessFunction<String, JSONObject>() {
            @Override
            public void processElement(String jsonStr, ProcessFunction<String, JSONObject>.Context ctx, Collector<JSONObject> out) throws Exception {
                try {
                    JSONObject jsonObject = JSON.parseObject(jsonStr);
                    out.collect(jsonObject);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // 5.使用FlinkCDC从配置表中读取配置信息---配置流
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname("hadoop102")
                .port(3306)
                .databaseList("gmall_config") // set captured database
                .tableList("gmall_config.table_process") // set captured table
                .username("root")
                .password("123456")
                .startupOptions(StartupOptions.initial())
                .deserializer(new JsonDebeziumDeserializationSchema()) // converts SourceRecord to JSON String
                .build();
        DataStreamSource<String> mySqlDS = env
                .fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "MySQL Source");

        //  6.对读取的配置流数据进行广播---广播流
        MapStateDescriptor<String, TableProcess> mapStateDescriptor
                = new MapStateDescriptor<>("mapStateDescriptor", String.class, TableProcess.class);
        BroadcastStream<String> broadcastDS = mySqlDS.broadcast(mapStateDescriptor);

        //  7.将主流业务数据和广播流配置数据进行关联---connect
        BroadcastConnectedStream<JSONObject, String> connectDS = jsonObjDS.connect(broadcastDS);

        //  8.对关联之后的结果进行处理---过滤出维度数据
        SingleOutputStreamOperator<JSONObject> dimDS = connectDS.process(new TableProcessFunction(mapStateDescriptor));

        //  9.将维度数据写到Phoenix表中
        dimDS.print(">>>");
        dimDS.addSink(new DimSinkFunction());
        env.execute();

    }
}
