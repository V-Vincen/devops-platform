package com.vincent.devops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DevOps 平台后端启动入口。
 *
 * <p>平台第一期采用模块化单体，先把项目、流水线、部署等能力集中在一个
 * 可独立运行的应用中，后续再根据真实负载拆分服务。</p>
 */
@SpringBootApplication
// 只扫描数据访问接口，避免把业务接口误注册成 MyBatis Mapper 代理。
@MapperScan({
        "com.vincent.devops.project.mapper",
        "com.vincent.devops.environment.mapper",
        "com.vincent.devops.microservice.mapper",
        "com.vincent.devops.auth.mapper"
})
public class DevopsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevopsApplication.class, args);
    }
}
