package com.atguigu.gmall.realtime.utils;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class PhoenixUtil {

    //执行在phoenix中建表以及向表中插入数据
    public static void executeSql(String sql){
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            //建立连接
            conn = DruidDSUtil.getConnection();
            //获取数据库操作对象
            ps = conn.prepareStatement(sql);
            //执行SQL语句
            ps.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            //释放资源
            if(ps != null){
                try {
                    ps.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if(conn != null){
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
