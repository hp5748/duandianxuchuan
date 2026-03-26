package com.example.streaming.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 演示应用启动类
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("\n==========================================");
        System.out.println("流式代理下载服务已启动!");
        System.out.println("测试页面: http://localhost:8080/download.html");
        System.out.println("API 接口: http://localhost:8080/api/proxy/download?url=xxx");
        System.out.println("==========================================\n");
    }
}
