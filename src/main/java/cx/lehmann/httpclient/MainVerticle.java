package cx.lehmann.httpclient;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.VerticleBase;
import io.vertx.core.buffer.Buffer;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends VerticleBase {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public Future<?> start() {
        HttpClient client = vertx.createHttpClient();

        return vertx.createHttpServer().requestHandler(req -> {
            CompletableFuture<Buffer> future = new CompletableFuture<>();
            req.bodyHandler(body -> {
                future.complete(body);
            });
            final HttpServerResponse response = req.response();
            client.request(new RequestOptions().setMethod(HttpMethod.POST).setHost("www.lehmann.cx").setPort(443)
                    .setSsl(true).setSslOptions(new ClientSSLOptions().setTrustAll(true)).setURI("/echo/echo.php"))
                    .onSuccess(clientRequest -> {
                        if (!future.isDone()) {
                            logger.info("trying to join future");
                            future.join();
                        }
                        Buffer buffer = future.getNow(null);
                        logger.info("body: " + buffer);
                        String replaced = buffer.toString().replace("abc", "xyzxyz");
                        logger.info("replaced body: " + replaced);
                        clientRequest.end(replaced);
                        MultiMap headers = req.headers();
                        headers.remove("Content-Length");
                        clientRequest.headers().setAll(headers);
                        clientRequest.response().onSuccess(clientResponse -> {
                            response.setStatusCode(clientResponse.statusCode()).headers()
                                    .setAll(clientResponse.headers());
                            clientResponse.bodyHandler(responseBody -> {
                                logger.info("body:" + responseBody);
                                response.end(responseBody);
                            });
                        }).onFailure(err -> {
                            logger.error("Unable to receive proxied response", err);
                            if (!response.ended()) {
                                response.setStatusCode(502).end();
                            }
                        });
                    }).onFailure(err -> {
                        logger.error("Unable to create proxied request", err);
                        response.setStatusCode(502).end();
                    });

        }).listen(8888).onSuccess(_ -> {
            logger.info("HTTP server started on port 8888");
        });
    }
}
