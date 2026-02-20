package com.iovchukandrew.dropvox.metadata.db;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import java.util.HashMap;
import java.util.Map;

//TODO Make pgPoolHolder? How many services will use it? Should we create separate pool for diff DAOs?
public class PgPoolCreator {

    public static Pool create(Vertx vertx, JsonObject config) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(config.getString("db.host"))
                .setPort(config.getInteger("db.port"))
                .setDatabase(config.getString("db.database"))
                .setUser(config.getString("db.user"))
                .setPassword(config.getString("db.password"));

        setDefaultScheme(connectOptions, config.getString("db.scheme"));

        PoolOptions poolOptions = new PoolOptions().setMaxSize(config.getInteger("db.pool.maxSize"));

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
