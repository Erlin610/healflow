package com.healflow.platform;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HealflowPlatformApplicationTest {

  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void contextLoads() throws Exception {
    assertNotNull(dataSource);
    assertNotNull(entityManagerFactory);

    try (var connection = dataSource.getConnection()) {
      assertNotNull(connection.getMetaData());
    }
  }
}

