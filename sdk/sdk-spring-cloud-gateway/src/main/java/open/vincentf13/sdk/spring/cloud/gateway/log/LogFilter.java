package open.vincentf13.sdk.spring.cloud.gateway.log;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class LogFilter implements GlobalFilter, Ordered {

  private final LogService logService;

  public LogFilter(LogService logService) {
    this.logService = logService;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    logService.logRequest(exchange);
    long startTime = System.currentTimeMillis();

    ServerWebExchange targetExchange = exchange;
    if (logService.isDebugEnabled()) {
      ServerHttpResponse originalResponse = exchange.getResponse();
      ServerHttpResponseDecorator responseDecorator =
          new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
              if (body instanceof Flux) {
                Flux<? extends DataBuffer> flux = (Flux<? extends DataBuffer>) body;
                return super.writeWith(
                    flux.map(
                        dataBuffer -> {
                          byte[] content = new byte[dataBuffer.readableByteCount()];
                          dataBuffer.read(content);
                          logService.appendResponseBody(exchange, content);
                          DataBufferUtils.release(dataBuffer);
                          return originalResponse.bufferFactory().wrap(content);
                        }));
              }
              return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                Publisher<? extends Publisher<? extends DataBuffer>> body) {
              return super.writeAndFlushWith(
                  Flux.from(body)
                      .map(
                          inner ->
                              Flux.from(inner)
                                  .map(
                                      dataBuffer -> {
                                        byte[] content = new byte[dataBuffer.readableByteCount()];
                                        dataBuffer.read(content);
                                        logService.appendResponseBody(exchange, content);
                                        DataBufferUtils.release(dataBuffer);
                                        return originalResponse.bufferFactory().wrap(content);
                                      })));
            }
          };
      targetExchange = exchange.mutate().response(responseDecorator).build();
    }

    return chain
        .filter(targetExchange)
        .doOnError(ex -> logService.logForwardFailure(exchange, ex))
        .then(
            Mono.fromRunnable(
                () -> {
                  long duration = System.currentTimeMillis() - startTime;
                  logService.logResponse(exchange, duration);
                }));
  }

  @Override
  public int getOrder() {
    return -1;
  }
}
