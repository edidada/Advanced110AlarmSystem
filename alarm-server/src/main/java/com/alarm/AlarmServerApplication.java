package com.alarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 110 告警系统启动入口：com.alarm 基础包扫描全部模块。 */
@SpringBootApplication
@EnableScheduling
public class AlarmServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlarmServerApplication.class, args);
    }
}
