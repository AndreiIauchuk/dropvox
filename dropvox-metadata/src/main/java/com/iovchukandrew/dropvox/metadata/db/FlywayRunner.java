package com.iovchukandrew.dropvox.metadata.db;

import io.vertx.core.json.JsonObject;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlywayRunner {
    private static final Logger log = LoggerFactory.getLogger(FlywayRunner.class);

    public MigrateResult runMigration(JsonObject config) {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                config.getString("db.host"),
                config.getInteger("db.port"),
                config.getString("db.database"));
        String user = config.getString("db.user");
        String password = config.getString("db.password");

        log.info("Running Flyway migration on {}", jdbcUrl);
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .schemas(config.getString("db.scheme"))
                .load();
        var result = flyway.migrate();
        log.info("Flyway migration complete with the status: {{}}", result.success);
        return result;
    }
}
