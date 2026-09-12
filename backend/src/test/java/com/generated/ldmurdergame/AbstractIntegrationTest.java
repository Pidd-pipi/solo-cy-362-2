package com.generated.ldmurdergame;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 集成测试基类：真实 Spring 上下文 + 真实 H2 数据库（schema.sql 自动建表）+ MockMvc，
 * 不启动网络端口、不依赖任何外部环境。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {
  @Autowired
  protected MockMvc mockMvc;
}
