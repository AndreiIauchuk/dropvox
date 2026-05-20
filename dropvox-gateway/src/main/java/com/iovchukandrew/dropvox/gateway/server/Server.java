package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.client.AuthServiceClient;
import com.iovchukandrew.dropvox.gateway.client.HttpHeader;
import com.iovchukandrew.dropvox.gateway.client.MetadataServiceClient;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.openapi.router.OpenAPIRoute;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.openapi.contract.OpenAPIContract;
import io.vertx.openapi.validation.RequestUtils;
import io.vertx.openapi.validation.ValidatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.Optional;
import java.util.UUID;

public class Server extends VerticleBase {
    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private static final String OPENAPI_SPEC_PATH = "swagger/openapi.json";
    private static final String SWAGGER_UI_WEBJAR_PATH = "META-INF/resources/webjars/swagger-ui/5.32.2";

    private final WebClient webClient;
    private final JsonObject config;

    public Server(WebClient webClient, JsonObject config) {
        this.webClient = webClient;
        this.config = config;
    }

    @Override
    public Future<HttpServer> start() {
        AuthServiceClient authServiceClient = new AuthServiceClient(
                webClient,
                config.getString("auth.service.host"),
                config.getInteger("auth.service.port"));

        MetadataServiceClient metadataServiceClient = new MetadataServiceClient(
                webClient,
                config.getString("metadata.service.host"),
                config.getInteger("metadata.service.port"));

        FileDownloadHandler fileDownloadHandler = new FileDownloadHandler(authServiceClient, metadataServiceClient);
        FileUploadInitHandler fileUploadInitHandler = new FileUploadInitHandler(authServiceClient, metadataServiceClient);
        FileUploadCompleteHandler fileUploadCompleteHandler =
                new FileUploadCompleteHandler(authServiceClient, metadataServiceClient);

        HttpServerOptions serverOptions = new HttpServerOptions().setHttp2ClearTextEnabled(false);
        int port = config.getInteger("server.port");
        return buildRouter(fileDownloadHandler, fileUploadInitHandler, fileUploadCompleteHandler)
                .compose(router -> vertx.createHttpServer(serverOptions)
                        .requestHandler(router)
                        .listen(port))
                .onSuccess(server -> log.info("Server started on port {}", server.actualPort()))
                .onFailure(e -> log.error("Failed to start gateway server", e));
    }

    private Future<Router> buildRouter(
            FileDownloadHandler fileDownloadHandler,
            FileUploadInitHandler fileUploadInitHandler,
            FileUploadCompleteHandler fileUploadCompleteHandler
    ) {
        return OpenAPIContract.from(vertx, OPENAPI_SPEC_PATH)
                .map(contract -> {
                    Router router = Router.router(vertx);
                    router.route().handler(this::traceIdMiddleware);
                    router.get("/health/live").handler(ctx -> ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("status", "UP").encode()));
                    router.get("/metrics").handler(PrometheusScrapingHandler.create());
                    configureDocsRoutes(router);

                    RouterBuilder routerBuilder = RouterBuilder.create(
                            vertx,
                            contract,
                            (routingContext, operation) -> RequestUtils.extract(
                                    routingContext.request(),
                                    operation,
                                    () -> Future.succeededFuture(
                                            routingContext.body() == null ? null : routingContext.body().buffer()
                                    ))
                    );
                    routerBuilder.rootHandler(BodyHandler.create());

                    mountOperation(routerBuilder, "downloadFile", fileDownloadHandler);
                    mountOperation(routerBuilder, "initFileUpload", fileUploadInitHandler);
                    mountOperation(routerBuilder, "completeFileUpload", fileUploadCompleteHandler);

                    router.route("/*").subRouter(routerBuilder.createRouter());
                    return router;
                });
    }

    private void traceIdMiddleware(RoutingContext ctx) {
        String traceId = Optional.ofNullable(ctx.request().getHeader(HttpHeader.TRACE_ID))
                .orElse(UUID.randomUUID().toString());

        MDC.put("traceId", traceId);
        ctx.response().putHeader(HttpHeader.TRACE_ID, traceId);
        ctx.put("traceId", traceId);
        ctx.addEndHandler(v -> MDC.remove("traceId"));
        ctx.next();
    }

    private void configureDocsRoutes(Router router) {
        router.getWithRegex("^/docs$").handler(ctx -> ctx.redirect("/docs/"));
        router.route("/docs/swagger-ui/*")
                .handler(StaticHandler.create(SWAGGER_UI_WEBJAR_PATH)
                        .setCachingEnabled(false)
                        .setDirectoryListing(false));
        router.route("/docs/*")
                .handler(StaticHandler.create("swagger")
                        .setCachingEnabled(false)
                        .setDirectoryListing(false));
    }

    private void mountOperation(RouterBuilder routerBuilder, String operationId, Handler<RoutingContext> handler) {
        OpenAPIRoute route = routerBuilder.getRoute(operationId);
        if (route == null) {
            throw new IllegalStateException("OpenAPI operation is not defined: " + operationId);
        }

        route.addHandler(handler);
        route.addFailureHandler(this::handleRouteFailure);
    }

    private void handleRouteFailure(RoutingContext ctx) {
        if (ctx.response().ended()) {
            return;
        }

        Throwable failure = ctx.failure();
        Throwable validationFailure = extractValidatorException(failure);
        if (validationFailure != null) {
            ctx.response()
                    .setStatusCode(HttpStatusCode.BAD_REQUEST)
                    .putHeader("Content-Type", "text/plain")
                    .end(validationFailure.getMessage());
            return;
        }

        log.error("Request processing failed", failure);
        ctx.response()
                .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                .putHeader("Content-Type", "text/plain")
                .end(failure == null ? "Internal Server Error" : failure.getMessage());
    }

    private Throwable extractValidatorException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ValidatorException) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }
}
