package com.iovchukandrew.dropvox.metadata;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class ConfigRetrieverFactory {

    private ConfigRetrieverFactory() {
    }

    public static ConfigRetriever create(Vertx vertx) {
        String propertiesFile = System.getenv("PROP_FILE");
        if (propertiesFile == null) {
            propertiesFile = "application.properties";
        }

        ConfigStoreOptions fileStore = new ConfigStoreOptions()
                .setType("file")
                .setFormat("properties")
                .setConfig(new JsonObject().put("path", propertiesFile));

        // Env vars override values from properties file.
        ConfigStoreOptions envStore = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject());

        return ConfigRetriever.create(
                vertx,
                new ConfigRetrieverOptions()
                        .addStore(fileStore)
                        .addStore(envStore));
    }
}
