package org.folio.rtaccache.perf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

final class RtacPerfDbObjects {

  private RtacPerfDbObjects() {
  }

  static void ensureMultiTenantFunction(Connection connection) throws SQLException {
    String sql = readClasspathUtf8("db/changelog/changes/create-rtac-holdings-multi-tenant-function.sql");
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static String readClasspathUtf8(String path) {
    try {
      return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read classpath resource: " + path, e);
    }
  }
}

