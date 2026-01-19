package com.healflow.platform.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class H2IncidentStatusEnumMigrationTest {

  @Test
  void migratesOldEnumToIncludeSkipInDesiredOrder() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:incident-status-migration-test;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        "CREATE TABLE incidents (id VARCHAR(10) PRIMARY KEY, status ENUM('ANALYZING','FIXED','IGNORED','OPEN','PENDING_REVIEW','REGRESSION') NOT NULL)");
    jdbc.execute("INSERT INTO incidents (id, status) VALUES ('1', 'ANALYZING')");
    jdbc.execute("INSERT INTO incidents (id, status) VALUES ('2', 'FIXED')");
    jdbc.execute("INSERT INTO incidents (id, status) VALUES ('3', 'IGNORED')");
    jdbc.execute("INSERT INTO incidents (id, status) VALUES ('4', 'OPEN')");
    jdbc.execute("INSERT INTO incidents (id, status) VALUES ('5', 'PENDING_REVIEW')");
    jdbc.execute("INSERT INTO incidents (id, status) VALUES ('6', 'REGRESSION')");

    H2IncidentStatusEnumMigration migration = new H2IncidentStatusEnumMigration(dataSource);
    migration.migrateIfNeeded();

    assertEquals("ANALYZING", jdbc.queryForObject("SELECT status FROM incidents WHERE id='1'", String.class));
    assertEquals("FIXED", jdbc.queryForObject("SELECT status FROM incidents WHERE id='2'", String.class));
    assertEquals("IGNORED", jdbc.queryForObject("SELECT status FROM incidents WHERE id='3'", String.class));
    assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM incidents WHERE id='4'", String.class));
    assertEquals("PENDING_REVIEW", jdbc.queryForObject("SELECT status FROM incidents WHERE id='5'", String.class));
    assertEquals("REGRESSION", jdbc.queryForObject("SELECT status FROM incidents WHERE id='6'", String.class));

    assertDoesNotThrow(() -> jdbc.execute("INSERT INTO incidents (id, status) VALUES ('7', 'SKIP')"));

    List<String> enumValues =
        jdbc.queryForList(
            """
            SELECT EV.VALUE_NAME
            FROM INFORMATION_SCHEMA.COLUMNS C
            JOIN INFORMATION_SCHEMA.ENUM_VALUES EV ON EV.ENUM_IDENTIFIER = C.DTD_IDENTIFIER
            WHERE C.TABLE_NAME = 'INCIDENTS' AND C.COLUMN_NAME = 'STATUS'
            ORDER BY EV.VALUE_ORDINAL
            """,
            String.class);

    assertEquals(
        List.of("OPEN", "SKIP", "ANALYZING", "PENDING_REVIEW", "FIXED", "REGRESSION", "IGNORED"),
        enumValues);
  }
}
