package com.atguigu.gmall.realtime.beans;

import lombok.Data;

/**
 * @author zhangxin
 * @Description 配置表对象
 * @createTime 2023年01月30日 15:50:00
 */
@Data
public class TableProcess {
    //来源表
    String sourceTable;
    //来源类型
    String sourceType;
    //输出表
    String sinkTable;
    //输出类型
    String sinkType;
    //输出字段
    String sinkColumns;
    //主键字段
    String sinkPk;
    //建表扩展
    String sinkExtend;

}
