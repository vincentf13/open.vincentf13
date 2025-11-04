package open.vincentf13.sdk.spring.cloud.gateway.filter;

import open.vincentf13.sdk.core.log.OpenLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI forwardUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);

        String routeId = route != null ? route.getId() : "unknown";
        String targetUri;
        if (forwardUrl != null) {
            targetUri = forwardUrl.toString();
        } else if (route != null && route.getUri() != null) {
            targetUri = route.getUri().toString();
        } else {
            targetUri = "unknown";
        }

        ServiceInstance serviceInstance = resolveServiceInstance(exchange);
        String instanceDesc = formatServiceInstance(serviceInstance);

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        OpenLog.info(log, "GatewayRequest", "Forwarding request",
                "method", method,
                "uri", request.getURI(),
                "routeId", routeId,
                "target", targetUri,
                "instance", instanceDesc);

        request.getHeaders().forEach((name, values) ->
                OpenLog.debug(log, "GatewayRequestHeader", () -> "Request header", "name", name, "values", values));
        if (route != null) {
            OpenLog.debug(log, "GatewayRoute", () -> "Resolved route",
                    "id", route.getId(),
                    "uri", route.getUri(),
                    "metadata", route.getMetadata());
        }
        if (forwardUrl != null) {
            OpenLog.debug(log, "GatewayForwardUrl", () -> "Forward URL", "url", forwardUrl);
        }
        if (serviceInstance != null) {
            OpenLog.debug(log, "GatewayServiceInstance", () -> "Service instance resolved",
                    "serviceId", serviceInstance.getServiceId(),
                    "host", serviceInstance.getHost(),
                    "port", serviceInstance.getPort());
        } else {
            OpenLog.debug(log, "GatewayServiceInstancePending", () -> "Service instance not yet resolved",
                    "routeId", routeId);
        }

        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .doOnError(ex -> OpenLog.error(log, "GatewayForwardFailed", "Forwarding failed", ex,
                        "routeId", routeId,
                        "target", targetUri))
                .then(Mono.fromRunnable(() -> {
                    ServerHttpResponse response = exchange.getResponse();
                    long duration = System.currentTimeMillis() - startTime;

                    URI finalForwardUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
                    String finalTarget = finalForwardUrl != null ? finalForwardUrl.toString() : targetUri;
                    ServiceInstance finalInstance = resolveServiceInstance(exchange);
                    String finalInstanceDesc = formatServiceInstance(finalInstance);

                    OpenLog.info(log, "GatewayResponse", "Response completed",
                            "status", response.getStatusCode(),
                            "durationMs", duration,
                            "routeId", routeId,
                            "target", finalTarget,
                            "instance", finalInstanceDesc);
                    if (finalForwardUrl != null && forwardUrl == null) {
                        OpenLog.debug(log, "GatewayForwardUrlResolved", () -> "Forward URL resolved", "url", finalForwardUrl);
                    }
                    if (finalInstance != null && serviceInstance == null) {
                        OpenLog.debug(log, "GatewayServiceInstanceResolved", () -> "Service instance resolved after filter",
                                "serviceId", finalInstance.getServiceId(),
                                "host", finalInstance.getHost(),
                                "port", finalInstance.getPort());
                    }
                    response.getHeaders().forEach((name, values) ->
                            OpenLog.debug(log, "GatewayResponseHeader", () -> "Response header", "name", name, "values", values));
                }));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private ServiceInstance resolveServiceInstance(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR);
        if (attr instanceof ServiceInstance) {
            return (ServiceInstance) attr;
        }
        if (attr instanceof Response) {
            Object server = ((Response<?>) attr).getServer();
            if (server instanceof ServiceInstance) {
                return (ServiceInstance) server;
            }
        }
        return null;
    }

    private String formatServiceInstance(ServiceInstance instance) {
        if (instance == null) {
            return "unresolved";
        }
        String serviceId = instance.getServiceId();
        String host = instance.getHost();
        int port = instance.getPort();
        String scheme = instance.getScheme();
        if (scheme == null || scheme.isEmpty()) {
            scheme = instance.isSecure() ? "https" : "http";
        }
        return String.format("%s@%s:%d(%s)", serviceId, host, port, scheme);
    }

}
