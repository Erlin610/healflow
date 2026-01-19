package com.healflow.platform.config;

import java.sql.Connection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class H2IncidentStatusEnumMigration implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(H2IncidentStatusEnumMigration.class);

  private static final String INCIDENTS_TABLE = "INCIDENTS";
  private static final String STATUS_COLUMN = "STATUS";

  // Keep in sync with com.healflow.common.enums.IncidentStatus.
  private static final List<String> DESIRED_ORDER =
      List.of("OPEN", "SKIP", "ANALYZING", "PENDING_REVIEW", "FIXED", "REGRESSION", "IGNORED");

  private final DataSource dataSource;
  private final JdbcTemplate jdbcTemplate;

  public H2IncidentStatusEnumMigration(DataSource dataSource) {
    this.dataSource = dataSource;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Override
  public void run(ApplicationArguments args) {
    migrateIfNeeded();
  }

  void migrateIfNeeded() {
    if (!isH2()) {
      return;
    }
    if (!tableExists(INCIDENTS_TABLE)) {
      return;
    }
    if (!isEnumColumn(INCIDENTS_TABLE, STATUS_COLUMN)) {
      return;
    }

    List<String> currentOrder = loadEnumValues(INCIDENTS_TABLE, STATUS_COLUMN);
    if (currentOrder.isEmpty()) {
      return;
    }

    List<String> desiredOrder = mergeDesiredWithExisting(currentOrder);
    if (currentOrder.equals(desiredOrder)) {
      return;
    }

    jdbcTemplate.execute(buildAlterEnumSql("incidents", "status", desiredOrder));
    log.info("Migrated H2 enum incidents.status: {} -> {}", currentOrder, desiredOrder);
  }

  private boolean isH2() {
    try (Connection connection = dataSource.getConnection()) {
      String url = connection.getMetaData().getURL();
      return url != null && url.startsWith("jdbc:h2:");
    } catch (Exception e) {
      return false;
    }
  }

  private boolean tableExists(String tableName) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
            Integer.class,
            tableName);
    return count != null && count > 0;
  }

  private boolean isEnumColumn(String tableName, String columnName) {
    List<String> dataTypes =
        jdbcTemplate.queryForList(
            "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
            String.class,
            tableName,
            columnName);
    return dataTypes.stream().anyMatch("ENUM"::equalsIgnoreCase);
  }

  private List<String> loadEnumValues(String tableName, String columnName) {
    return jdbcTemplate.queryForList(
        """
        SELECT EV.VALUE_NAME
        FROM INFORMATION_SCHEMA.COLUMNS C
        JOIN INFORMATION_SCHEMA.ENUM_VALUES EV ON EV.ENUM_IDENTIFIER = C.DTD_IDENTIFIER
        WHERE C.TABLE_NAME = ? AND C.COLUMN_NAME = ?
        ORDER BY EV.VALUE_ORDINAL
        """,
        String.class,
        tableName,
        columnName);
  }

  private static List<String> mergeDesiredWithExisting(List<String> existing) {
    Set<String> existingSet = Set.copyOf(existing);
    LinkedHashSet<String> ordered = new LinkedHashSet<>();

    for (String value : DESIRED_ORDER) {
      if ("SKIP".equals(value) || existingSet.contains(value)) {
        ordered.add(value);
      }
    }
    ordered.addAll(existing);
    return List.copyOf(ordered);
  }

  private static String buildAlterEnumSql(String tableName, String columnName, List<String> values) {
    StringBuilder sql = new StringBuilder();
    sql.append("ALTER TABLE ").append(tableName).append(" ALTER COLUMN ").append(columnName).append(" ENUM(");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sql.append(",");
      }
      sql.append("'").append(values.get(i).replace("'", "''")).append("'");
    }
    sql.append(")");
    return sql.toString();
  }
}

