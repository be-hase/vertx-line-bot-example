package com.be_hase.vertx.linebot;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KitchenSinkApplication {
    public static void main(String[] args) throws IOException {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                           "io.vertx.core.logging.SLF4JLogDelegateFactory");
        Json.mapper.registerModule(new JavaTimeModule())
                   .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                   .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        Json.prettyMapper.registerModule(new JavaTimeModule())
                         .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                         .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);

        VertxOptions vertxOptions = new VertxOptions();
        Vertx vertx = Vertx.vertx(vertxOptions);

        JsonObject config = new JsonObject();
        config.put("accessToken", System.getenv("LINE_ACCESS_TOKEN"))
              .put("channelSecret", System.getenv("LINE_CHANNEL_SECRET"))
              .put("bot.port", Integer.valueOf(Optional.ofNullable(System.getenv("LINE_BOT_PORT"))
                                                       .orElse("8080")));

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setInstances(vertxOptions.getEventLoopPoolSize())
                         .setConfig(config);

        vertx.deployVerticle("com.be_hase.vertx.linebot.WebhookServer", deploymentOptions);
    }
}
