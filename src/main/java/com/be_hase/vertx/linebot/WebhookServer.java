package com.be_hase.vertx.linebot;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.event.BeaconEvent;
import com.linecorp.bot.model.event.CallbackRequest;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.FollowEvent;
import com.linecorp.bot.model.event.JoinEvent;
import com.linecorp.bot.model.event.LeaveEvent;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.UnfollowEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.LocationMessageContent;
import com.linecorp.bot.model.event.message.MessageContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.message.VideoMessageContent;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import io.vertx.rxjava.ext.web.handler.LoggerHandler;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;

@Slf4j
public class WebhookServer extends AbstractVerticle {
    private LineSignatureValidator lineSignatureValidator;
    private MessagingApiClient messagingApiClient;

    @Override
    public void start() throws Exception {
        String channelSecret = config().getString("channelSecret");
        String accessToken = config().getString("accessToken");

        lineSignatureValidator = new LineSignatureValidator(channelSecret.getBytes(StandardCharsets.UTF_8));
        messagingApiClient = new MessagingApiClient(vertx, accessToken);

        final Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
        router.route().handler(BodyHandler.create());
        router.route(HttpMethod.POST, "/callback").handler(this::receiveWebhook);
        router.route(HttpMethod.GET, "/test").handler(context -> {
            log.info("test");
            context.response()
                   .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "text/plain")
                   .end(HttpResponseStatus.OK.reasonPhrase());
        });

        final HttpServer httpServer = vertx.createHttpServer();
        httpServer.requestHandler(router::accept);
        httpServer.listen(config().getInteger("port", 8080));
    }

    private void receiveWebhook(RoutingContext context) {
        final String bodyAsString = context.getBodyAsString();
        final String headerSignature = Optional.ofNullable(context.request().getHeader("X-Line-Signature"))
                                               .orElse("");

        if (!lineSignatureValidator.validateSignature(bodyAsString.getBytes(StandardCharsets.UTF_8),
                                                      headerSignature)) {
            context.response()
                   .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                   .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "text/plain")
                   .end(HttpResponseStatus.BAD_REQUEST.reasonPhrase());
            return;
        }

        parseBody(bodyAsString).subscribe(
                event -> {
                },
                throwable -> {
                    log.error("Error: {}, {}",
                              throwable.getClass().getName(), throwable.getMessage(), throwable);
                    context.response()
                           .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                           .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "text/plain")
                           .end(HttpResponseStatus.INTERNAL_SERVER_ERROR.reasonPhrase());
                },
                () -> context.response()
                             .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "text/plain")
                             .end(HttpResponseStatus.OK.reasonPhrase())
        );
    }

    private Observable<Event> parseBody(String body) {
        final CallbackRequest callbackRequest = Json.decodeValue(body, CallbackRequest.class);
        return Observable.from(callbackRequest.getEvents());
    }

    private Observable<Boolean> handleEvent(Event event) {
        if (event instanceof MessageEvent) {
            MessageEvent messageEvent = (MessageEvent) event;
            MessageContent messageContent = messageEvent.getMessage();

            if (messageContent instanceof TextMessageContent) {
                return handleMessageEvent(messageEvent, (TextMessageContent) messageContent);
            } else if (messageContent instanceof ImageMessageContent) {
                return handleMessageEvent(messageEvent, (ImageMessageContent) messageContent);
            } else if (messageContent instanceof VideoMessageContent) {
                return handleMessageEvent(messageEvent, (VideoMessageContent) messageContent);
            } else if (messageContent instanceof AudioMessageContent) {
                return handleMessageEvent(messageEvent, (AudioMessageContent) messageContent);
            } else if (messageContent instanceof LocationMessageContent) {
                return handleMessageEvent(messageEvent, (LocationMessageContent) messageContent);
            } else if (messageContent instanceof StickerMessageContent) {
                return handleMessageEvent(messageEvent, (StickerMessageContent) messageContent);
            } else {
                log.info("Received message(Ignored). messageEvent={}", messageEvent);
                return Observable.just(true);
            }
        } else if (event instanceof UnfollowEvent) {
            return handleUnfollowEvent((UnfollowEvent) event);
        } else if (event instanceof FollowEvent) {
            return handleFollowEvent((FollowEvent) event);
        } else if (event instanceof JoinEvent) {
            return handleJoinEvent((JoinEvent) event);
        } else if (event instanceof LeaveEvent) {
            return handleLeaveEvent((LeaveEvent) event);
        } else if (event instanceof PostbackEvent) {
            return handlePostbackEvent((PostbackEvent) event);
        } else if (event instanceof BeaconEvent) {
            return handleBeaconEvent((BeaconEvent) event);
        } else {
            log.info("Received message(Ignored). event={}", event);
            return Observable.just(true);
        }
    }

    private Observable<Boolean> handleMessageEvent(MessageEvent event, TextMessageContent messageContent) {
        final String replyToken = event.getReplyToken();
        final String text = messageContent.getText();

        switch (text) {
            default:
                break;
        }

        return Observable.just(true);
    }

    private Observable<Boolean> handleMessageEvent(MessageEvent event, ImageMessageContent messageContent) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleMessageEvent(MessageEvent event, VideoMessageContent messageContent) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleMessageEvent(MessageEvent event, AudioMessageContent messageContent) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleMessageEvent(MessageEvent event, LocationMessageContent messageContent) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleMessageEvent(MessageEvent event, StickerMessageContent messageContent) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleUnfollowEvent(UnfollowEvent event) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleFollowEvent(FollowEvent event) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleJoinEvent(JoinEvent event) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleLeaveEvent(LeaveEvent event) {
        return Observable.just(true);
    }

    private Observable<Boolean> handlePostbackEvent(PostbackEvent event) {
        return Observable.just(true);
    }

    private Observable<Boolean> handleBeaconEvent(BeaconEvent event) {
        return Observable.just(true);
    }
}
