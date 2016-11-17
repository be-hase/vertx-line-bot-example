package com.be_hase.vertx.linebot;

import com.fasterxml.jackson.databind.DeserializationFeature;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KitchenSinkApplication {
    public static void main(String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                           "io.vertx.core.logging.SLF4JLogDelegateFactory");
        Json.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Json.prettyMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        VertxOptions vertxOptions = new VertxOptions();
        Vertx vertx = Vertx.vertx(vertxOptions);

        JsonObject config = new JsonObject();
        config.put("accessToken", "hoge")
              .put("channelSecret", "hoge");

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setInstances(vertxOptions.getEventLoopPoolSize())
                         .setConfig(config);

        vertx.deployVerticle("com.be_hase.vertx.linebot.WebhookServer", deploymentOptions);
    }
}
