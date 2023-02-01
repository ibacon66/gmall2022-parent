package com.atguigu.gmall.realtime.utils;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

public class MyKafkaUtil {
    private static final String KAFKA_SERVER = "hadoop102:9092,hadoop103:9092,hadoop104:9092";

    //获取消费者对象
    public static FlinkKafkaConsumer<String> getKafkaConsumer(String topic, String groupId) {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        //注意:通过SimpleStringSchema对读取的消息进行反序列化，如果消费为空，在创建String对象的时候，会有问题，我们需要自己定义反序列化操作
        // FlinkKafkaConsumer kafkaConsumer = new FlinkKafkaConsumer<String>(topic, new SimpleStringSchema(), props);
        FlinkKafkaConsumer<String> kafkaConsumer = new FlinkKafkaConsumer<>(topic, new KafkaDeserializationSchema<String>() {
            @Override
            public boolean isEndOfStream(String nextElement) {
                return false;
            }

            @Override
            public String deserialize(ConsumerRecord<byte[], byte[]> record) throws Exception {
                if (record.value() != null) {
                    return new String(record.value());
                }
                return null;
            }

            @Override
            public TypeInformation<String> getProducedType() {
                return TypeInformation.of(String.class);
            }
        }, props);
        return kafkaConsumer;
    }

    //获取生产者对象
    public static FlinkKafkaProducer<String> getKafkaProducer(String topic) {
        //注意：通过如下方式创建的FlinkKafkaProducer对象，默认Semantic.AT_LEAST_ONCE，并不能开启事务，也就是说不能保证生产数据的一致性
        //如果要想保证发送消息的一致性，需要在创建FlinkKafkaProducer对象的时候手动设置Semantic.EXACTLY_ONCE
        // FlinkKafkaProducer kafkaProducer = new FlinkKafkaProducer<String>("hadoop102:9092","dirty_data",new SimpleStringSchema());

        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        props.setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 15 * 60 * 1000 + "");

        FlinkKafkaProducer<String> kafkaProducer = new FlinkKafkaProducer<>(
                "default_topic",
                (KafkaSerializationSchema<String>) (str, timestamp) -> new ProducerRecord<>(topic, str.getBytes()),
                props,
                FlinkKafkaProducer.Semantic.EXACTLY_ONCE);
        return kafkaProducer;
    }

    public static String getTopicDbDDL(String groupId) {
        return "CREATE TABLE topic_db (\n" +
                "    `database` STRING,\n" +
                "    `table` STRING,\n" +
                "    `type` STRING,\n" +
                "    `ts` STRING,\n" +
                "    `data` MAP<string, string>,\n" +
                "    `old` MAP<string, string>,\n" +
                "     proc_time AS PROCTIME()\n" +
                ") " + getKafkaDDL("topic_db", groupId);
    }

    //返回kafka连接器的连接属性
    public static String getKafkaDDL(String topic, String groupId) {
        return "WITH (\n" +
                "  'connector' = 'kafka',\n" +
                "  'topic' = '" + topic + "',\n" +
                "  'properties.bootstrap.servers' = '" + KAFKA_SERVER + "',\n" +
                "  'properties.group.id' = '" + groupId + "',\n" +
                "  'scan.startup.mode' = 'group-offsets',\n" +
                "  'format' = 'json'\n" +
                ")";
    }


    //获取upsert-kafka连接器的连接属性
    public static String getUpsertKafkaDDL(String topic) {
        return " WITH (\n" +
                "  'connector' = 'upsert-kafka',\n" +
                "  'topic' = '" + topic + "',\n" +
                "  'properties.bootstrap.servers' = '" + KAFKA_SERVER + "',\n" +
                "  'key.format' = 'json',\n" +
                "  'value.format' = 'json'\n" +
                ")";
    }
}
