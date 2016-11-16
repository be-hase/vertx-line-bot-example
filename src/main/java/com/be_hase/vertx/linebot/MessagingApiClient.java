package com.be_hase.vertx.linebot;

import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.rxjava.core.RxHelper;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import lombok.Getter;
import rx.Observable;

public class MessagingApiClient {
    public static final String BASE_URL = "https://api.line.me";

    private final String accessToken;
    private final HttpClient httpClient;

    public MessagingApiClient(Vertx vertx, String accessToken) {
        this.accessToken = accessToken;
        httpClient = vertx.createHttpClient(
                new HttpClientOptions().setSsl(true)
                                       .setLogActivity(true)
                                       .setMaxPoolSize(30)
        );
    }

    public Observable<BotApiResponse> replyMessage(ReplyMessage replyMessage) {
        HttpClientRequest request = httpClient.postAbs(BASE_URL + "/v2/bot/message/reply");
        return post(request, Json.encode(replyMessage), BotApiResponse.class);
    }

    public Observable<BotApiResponse> pushMessage(PushMessage pushMessage) {
        HttpClientRequest request = httpClient.postAbs(BASE_URL + "/v2/bot/message/push");
        return post(request, Json.encode(pushMessage), BotApiResponse.class);
    }

    public Observable<BotApiResponse> leaveGroup(String groupId) {
        HttpClientRequest request = httpClient.postAbs(BASE_URL + "/v2/bot/group/" + groupId + "/leave");
        return post(request, "", BotApiResponse.class);
    }

    public Observable<BotApiResponse> leaveRoom(String roomId) {
        HttpClientRequest request = httpClient.postAbs(BASE_URL + "/v2/bot/room/" + roomId + "/leave");
        return post(request, "", BotApiResponse.class);
    }

    public Observable<UserProfileResponse> getProfile(String userId) {
        HttpClientRequest request = httpClient.getAbs(BASE_URL + "/v2/bot/profile/" + userId);
        return get(request, UserProfileResponse.class);
    }

    public Observable<HttpClientResponse> getMessageContent(String messageId) {
        return Observable.create(subscriber -> {
            HttpClientRequest request = httpClient.getAbs(
                    BASE_URL + "/v2/bot/message/" + messageId + "/content");
            request.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + accessToken);

            request.toObservable()
                   .filter(this::filterSuccessResponse)
                   .subscribe(subscriber);

            request.end();
        });
    }

    private <T> Observable<T> post(HttpClientRequest request, String body, Class<T> clazz) {
        return Observable.create(subscriber -> {
            request.setTimeout(5000)
                   .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + accessToken)
                   .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json; charset=utf-8");

            request.toObservable()
                   .filter(this::filterSuccessResponse)
                   .flatMap(response -> response.toObservable())
                   .lift(RxHelper.unmarshaller(clazz))
                   .subscribe(subscriber);

            request.end(body);
        });
    }

    private <T> Observable<T> get(HttpClientRequest request, Class<T> clazz) {
        return Observable.create(subscriber -> {
            request.setTimeout(5000)
                   .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + accessToken);

            request.toObservable()
                   .filter(this::filterSuccessResponse)
                   .flatMap(response -> response.toObservable())
                   .lift(RxHelper.unmarshaller(clazz))
                   .subscribe(subscriber);

            request.end();
        });
    }

    private boolean filterSuccessResponse(HttpClientResponse response) {
        if (response.statusCode() / 100 != 2) {
            throw new ErrorResponseException(response.statusCode());
        }
        return true;
    }

    @Getter
    public static class ErrorResponseException extends RuntimeException {
        private final int statusCode;

        public ErrorResponseException(int statusCode) {
            this.statusCode = statusCode;
        }
    }
}
