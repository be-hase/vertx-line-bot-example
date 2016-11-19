package com.be_hase.vertx.linebot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.action.URIAction;
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
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.message.ImagemapMessage;
import com.linecorp.bot.model.message.LocationMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.imagemap.ImagemapArea;
import com.linecorp.bot.model.message.imagemap.ImagemapBaseSize;
import com.linecorp.bot.model.message.imagemap.MessageImagemapAction;
import com.linecorp.bot.model.message.imagemap.URIImagemapAction;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.CarouselColumn;
import com.linecorp.bot.model.message.template.CarouselTemplate;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.file.AsyncFile;
import io.vertx.rxjava.core.file.FileSystem;
import io.vertx.rxjava.core.http.HttpClientResponse;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.streams.Pump;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import io.vertx.rxjava.ext.web.handler.LoggerHandler;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;

@Slf4j
public class WebhookServer extends AbstractVerticle {
    private LineSignatureValidator lineSignatureValidator;
    private MessagingApiClient messagingApiClient;

    private Path downloadTempDir;

    @Override
    public void start() throws Exception {
        String channelSecret = config().getString("channelSecret");
        String accessToken = config().getString("accessToken");

        lineSignatureValidator = new LineSignatureValidator(channelSecret.getBytes(StandardCharsets.UTF_8));
        messagingApiClient = new MessagingApiClient(vertx, accessToken);
        downloadTempDir = Files.createTempDirectory("line-bot");

        // Setting Router
        final Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
        router.route().handler(BodyHandler.create().setBodyLimit(10 * 1024 * 1024));
        router.route(HttpMethod.POST, "/callback").handler(this::receiveWebhook);
        router.route(HttpMethod.GET, "/test").handler(context -> {
            log.info("test");
            context.response()
                   .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "text/plain")
                   .end(HttpResponseStatus.OK.reasonPhrase());
        });
        router.route("/static/*").handler(StaticHandler.create());

        // Setting HttpServer
        final HttpServer httpServer = vertx.createHttpServer();
        httpServer.requestHandler(router::accept);
        httpServer.listen(config().getInteger("bot.port", 8080));
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

        parseBody(bodyAsString)
                .flatMap(event -> handleEvent(context, event))
                .subscribe(
                        result -> log.info("Handled event. result={}", result),
                        throwable -> {
                            log.error("Error: {}",
                                      throwable.getClass().getName(), throwable);
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

    private Observable handleEvent(RoutingContext context, Event event) {
        if (event instanceof MessageEvent) {
            MessageEvent messageEvent = (MessageEvent) event;
            MessageContent messageContent = messageEvent.getMessage();

            if (messageContent instanceof TextMessageContent) {
                return handleMessageEvent(context, messageEvent, (TextMessageContent) messageContent);
            } else if (messageContent instanceof ImageMessageContent) {
                return handleMessageEvent(context, messageEvent, (ImageMessageContent) messageContent);
            } else if (messageContent instanceof VideoMessageContent) {
                return handleMessageEvent(context, messageEvent, (VideoMessageContent) messageContent);
            } else if (messageContent instanceof AudioMessageContent) {
                return handleMessageEvent(context, messageEvent, (AudioMessageContent) messageContent);
            } else if (messageContent instanceof LocationMessageContent) {
                return handleMessageEvent(context, messageEvent, (LocationMessageContent) messageContent);
            } else if (messageContent instanceof StickerMessageContent) {
                return handleMessageEvent(context, messageEvent, (StickerMessageContent) messageContent);
            } else {
                log.info("Received message, but ignored due to unknown message content type. messageEvent={}",
                         messageEvent);
                return Observable.just(false);
            }
        } else if (event instanceof UnfollowEvent) {
            return handleUnfollowEvent(context, (UnfollowEvent) event);
        } else if (event instanceof FollowEvent) {
            return handleFollowEvent(context, (FollowEvent) event);
        } else if (event instanceof JoinEvent) {
            return handleJoinEvent(context, (JoinEvent) event);
        } else if (event instanceof LeaveEvent) {
            return handleLeaveEvent(context, (LeaveEvent) event);
        } else if (event instanceof PostbackEvent) {
            return handlePostbackEvent(context, (PostbackEvent) event);
        } else if (event instanceof BeaconEvent) {
            return handleBeaconEvent(context, (BeaconEvent) event);
        } else {
            log.info("Received message, but ignored due to unknown event. event={}", event);
            return Observable.just(false);
        }
    }

    private Observable handleMessageEvent(RoutingContext context, MessageEvent event,
                                          TextMessageContent messageContent) {
        final String replyToken = event.getReplyToken();
        final String text = messageContent.getText();

        switch (text) {
            case "profile": {
                String userId = event.getSource().getUserId();
                if (userId != null) {
                    Observable<UserProfileResponse> profileObs = messagingApiClient.getProfile(userId);
                    return profileObs.flatMap(profile -> reply(
                            replyToken,
                            Arrays.asList(
                                    new TextMessage("Display name: " + profile.getDisplayName()),
                                    new TextMessage(
                                            "Status message: " + profile.getStatusMessage())
                            )
                    ));
                } else {
                    return replyText(replyToken, "Bot can't use profile API without user ID");
                }
            }
            case "bye": {
                Source source = event.getSource();
                if (source instanceof GroupSource) {
                    return replyText(replyToken, "Leaving group").flatMap(
                            v -> messagingApiClient.leaveGroup(((GroupSource) source).getGroupId()));
                } else if (source instanceof RoomSource) {
                    return replyText(replyToken, "Leaving room").flatMap(
                            v -> messagingApiClient.leaveRoom(((RoomSource) source).getRoomId()));
                } else {
                    return replyText(replyToken, "Bot can't leave from 1:1 chat");
                }
            }
            case "confirm": {
                ConfirmTemplate confirmTemplate = new ConfirmTemplate(
                        "Do it?",
                        new MessageAction("Yes", "Yes!"),
                        new MessageAction("No", "No!")
                );
                TemplateMessage templateMessage = new TemplateMessage("Confirm alt text", confirmTemplate);
                return reply(replyToken, templateMessage);
            }
            case "buttons": {
                String imageUrl = createUri(context, "/static/buttons/1040.jpg");
                log.info("imageUrl={}", imageUrl);
                ButtonsTemplate buttonsTemplate = new ButtonsTemplate(
                        imageUrl,
                        "My button sample",
                        "Hello, my button",
                        Arrays.asList(
                                new URIAction("Go to line.me",
                                              "https://line.me"),
                                new PostbackAction("Say hello1",
                                                   "hello こんにちは"),
                                new PostbackAction("言 hello2",
                                                   "hello こんにちは",
                                                   "hello こんにちは"),
                                new MessageAction("Say message",
                                                  "Rice=米")
                        ));
                TemplateMessage templateMessage = new TemplateMessage("Button alt text", buttonsTemplate);
                return reply(replyToken, templateMessage);
            }
            case "carousel": {
                String imageUrl = createUri(context, "/static/buttons/1040.jpg");
                CarouselTemplate carouselTemplate = new CarouselTemplate(
                        Arrays.asList(
                                new CarouselColumn(imageUrl, "hoge", "fuga", Arrays.asList(
                                        new URIAction("Go to line.me",
                                                      "https://line.me"),
                                        new PostbackAction("Say hello1",
                                                           "hello こんにちは")
                                )),
                                new CarouselColumn(imageUrl, "hoge", "fuga", Arrays.asList(
                                        new PostbackAction("言 hello2",
                                                           "hello こんにちは",
                                                           "hello こんにちは"),
                                        new MessageAction("Say message",
                                                          "Rice=米")
                                ))
                        ));
                TemplateMessage templateMessage = new TemplateMessage("Carousel alt text", carouselTemplate);
                return reply(replyToken, templateMessage);
            }
            case "imagemap": {
                return reply(replyToken, new ImagemapMessage(
                        createUri(context, "/static/rich"),
                        "This is alt text",
                        new ImagemapBaseSize(1040, 1040),
                        Arrays.asList(
                                new URIImagemapAction(
                                        "https://store.line.me/family/manga/en",
                                        new ImagemapArea(
                                                0, 0, 520, 520
                                        )
                                ),
                                new URIImagemapAction(
                                        "https://store.line.me/family/music/en",
                                        new ImagemapArea(
                                                520, 0, 520, 520
                                        )
                                ),
                                new URIImagemapAction(
                                        "https://store.line.me/family/play/en",
                                        new ImagemapArea(
                                                0, 520, 520, 520
                                        )
                                ),
                                new MessageImagemapAction(
                                        "URANAI!",
                                        new ImagemapArea(
                                                520, 520, 520, 520
                                        )
                                )
                        )
                ));
            }
            default: {
                return replyText(replyToken, text);
            }
        }
    }

    private Observable handleMessageEvent(RoutingContext context, MessageEvent event,
                                          ImageMessageContent messageContent) {
        Observable<HttpClientResponse> responseObs =
                messagingApiClient.getMessageContent(messageContent.getId());
        return saveContent(responseObs, "jpg")
                .flatMap(fileName -> replyText(event.getReplyToken(), "Saved image."));
    }

    private Observable handleMessageEvent(RoutingContext context, MessageEvent event,
                                          VideoMessageContent messageContent) {
        Observable<HttpClientResponse> responseObs =
                messagingApiClient.getMessageContent(messageContent.getId());
        return saveContent(responseObs, "mp4")
                .flatMap(fileName -> replyText(event.getReplyToken(), "Saved movie."));
    }

    private Observable handleMessageEvent(RoutingContext context, MessageEvent event,
                                          AudioMessageContent messageContent) {
        Observable<HttpClientResponse> responseObs =
                messagingApiClient.getMessageContent(messageContent.getId());
        return saveContent(responseObs, "m4a")
                .flatMap(fileName -> replyText(event.getReplyToken(), "Saved audio."));
    }

    private Observable handleMessageEvent(RoutingContext context, MessageEvent event,
                                          LocationMessageContent messageContent) {
        return reply(event.getReplyToken(), new LocationMessage(
                messageContent.getTitle(),
                messageContent.getAddress(),
                messageContent.getLatitude(),
                messageContent.getLongitude()
        ));
    }

    private Observable handleMessageEvent(RoutingContext context, MessageEvent event,
                                          StickerMessageContent messageContent) {
        return reply(event.getReplyToken(), new StickerMessage(
                messageContent.getPackageId(), messageContent.getStickerId())
        );
    }

    private Observable handleUnfollowEvent(RoutingContext context, UnfollowEvent event) {
        log.info("Unfollowed this bot: {}", event);
        return Observable.just(true);
    }

    private Observable handleFollowEvent(RoutingContext context, FollowEvent event) {
        return replyText(event.getReplyToken(), "Got followed event");
    }

    private Observable handleJoinEvent(RoutingContext context, JoinEvent event) {
        return replyText(event.getReplyToken(), "Joined " + event.getSource());
    }

    private Observable handleLeaveEvent(RoutingContext context, LeaveEvent event) {
        log.info("Leaved this bot: {}", event);
        return Observable.just(true);
    }

    private Observable handlePostbackEvent(RoutingContext context, PostbackEvent event) {
        return replyText(event.getReplyToken(), "Got postback " + event.getPostbackContent().getData());
    }

    private Observable handleBeaconEvent(RoutingContext context, BeaconEvent event) {
        return replyText(event.getReplyToken(), "Got beacon message " + event.getBeacon().getHwid());
    }

    private Observable<BotApiResponse> reply(String replyToken, List<Message> messages) {
        return messagingApiClient.replyMessage(new ReplyMessage(replyToken, messages));
    }

    private Observable<BotApiResponse> reply(String replyToken, Message message) {
        return reply(replyToken, Collections.singletonList(message));
    }

    private Observable<BotApiResponse> replyText(String replyToken, String message) {
        return reply(replyToken, new TextMessage(message));
    }

    private String createUri(RoutingContext context, String path) {
        try {
            URL url = new URL(context.request().absoluteURI());
            URL newUrl = new URL("https", url.getHost(), url.getPort(), path);
            return newUrl.toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Observable<String> saveContent(Observable<HttpClientResponse> responseObs, String ext) {
        try {
            final FileSystem fileSystem = vertx.fileSystem();
            final Path tempFile = Files.createTempFile(downloadTempDir, "", "." + ext);

            Observable<AsyncFile> fileObs = fileSystem.openObservable(tempFile.toString(), new OpenOptions());

            Observable.zip(
                    responseObs,
                    fileObs,
                    (response, file) -> Pump.pump(response, file)
            ).subscribe(pump -> pump.start());

            log.info("Saved to {}", tempFile);
            return Observable.just(tempFile.getFileName().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
