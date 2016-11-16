package com.be_hase.vertx.linebot;

import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import lombok.Getter;
import rx.Observable;
import rx.subjects.ReplaySubject;

public class MessagingApiClient {
    public static final String BASE_URL = "https://api.line.me";

    private final String accessToken;
    private final HttpClient httpClient;

    public MessagingApiClient(Vertx vertx, String accessToken) {
        this.accessToken = accessToken;
        httpClient = vertx.createHttpClient(new HttpClientOptions().setSsl(true).setLogActivity(true));
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
        HttpClientRequest request = httpClient.getAbs(BASE_URL + "/v2/bot/message/" + messageId + "/content");
        request.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + accessToken);

        ReplaySubject<HttpClientResponse> subject = ReplaySubject.create();
        request.toObservable().subscribe(
                response -> {
                    if (response.statusCode() / 100 != 2) {
                        subject.onError(new ErrorResponseException(response.statusCode()));
                    }
                    subject.onNext(response);
                },
                throwable -> subject.onError(throwable)
        );

        request.end();
        return subject;
    }

    private <T> Observable<T> post(HttpClientRequest request, String body, Class<T> clazz) {
        request.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + accessToken)
               .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json; charset=utf-8");

        Observable<T> observable = toObservable(request, clazz);

        request.end(body);
        return observable;
    }

    private <T> Observable<T> get(HttpClientRequest request, Class<T> clazz) {
        request.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + accessToken);

        Observable<T> observable = toObservable(request, clazz);

        request.end();
        return observable;
    }

    private <T> Observable<T> toObservable(HttpClientRequest request, Class<T> clazz) {
        ReplaySubject<T> subject = ReplaySubject.create();
        request.toObservable().flatMap(response -> {
            if (response.statusCode() / 100 != 2) {
                throw new ErrorResponseException(response.statusCode());
            }
            return response.toObservable();
        }).subscribe(
                buffer -> {
                    subject.onNext(Json.decodeValue(buffer.toString("utf-8"), clazz));
                    subject.onCompleted();
                },
                throwable -> subject.onError(throwable)
        );
        return subject;
    }

    @Getter
    public static class ErrorResponseException extends RuntimeException {
        private final int statusCode;

        public ErrorResponseException(int statusCode) {
            this.statusCode = statusCode;
        }
    }
}
