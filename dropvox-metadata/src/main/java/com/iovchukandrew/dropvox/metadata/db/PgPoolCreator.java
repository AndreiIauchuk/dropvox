package com.iovchukandrew.dropvox.metadata.db;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

//TODO Make pgPoolHolder? How many services will use it? Should we create separate pool for diff DAOs?
public class PgPoolCreator {
    private static final Logger log = LoggerFactory.getLogger(PgPoolCreator.class);

    public static Pool create(Vertx vertx, JsonObject config) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(config.getString("db.host"))
                .setPort(config.getInteger("db.port"))
                .setDatabase(config.getString("db.database"))
                .setUser(config.getString("db.user"))
                .setPassword(config.getString("db.password"));

        setDefaultScheme(connectOptions, config.getString("db.scheme"));

        PoolOptions poolOptions = new PoolOptions().setMaxSize(config.getInteger("db.pool.maxSize"));

        log.info("Creating PostgreSQL pool with options: host={}, port={}, database={}, user={}, scheme={}, maxSize={}",
                connectOptions.getHost(), connectOptions.getPort(), connectOptions.getDatabase(),
                connectOptions.getUser(), config.getString("db.scheme"), poolOptions.getMaxSize());
                
        return PgBuilder.pool()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx)
                .build();
    }

    private static void setDefaultScheme(PgConnectOptions connectOptions, String scheme) {
        Map<String, String> props = new HashMap<>();
        props.put("search_path", scheme);
        connectOptions.setProperties(props);
    }
}
