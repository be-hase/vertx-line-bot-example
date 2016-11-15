package com.be_hase.vertx.linebot;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpClient;

public class MessagingApiClient {
    private final String accessToken;
    private final HttpClient httpClient;

    public MessagingApiClient(Vertx vertx, String accessToken) {
        this.accessToken = accessToken;
        httpClient = vertx.createHttpClient(new HttpClientOptions().setSsl(true));
    }
}
