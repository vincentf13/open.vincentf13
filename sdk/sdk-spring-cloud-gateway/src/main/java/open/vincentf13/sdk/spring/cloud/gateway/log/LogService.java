package open.vincentf13.sdk.spring.cloud.gateway.log;

import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.cloud.gateway.log.GatewayEventEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.List;

@Component
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);
    private static final String ATTR_INITIAL_FORWARD_URL = LogService.class.getName() + ".initialForwardUrl";
    private static final String ATTR_INITIAL_SERVICE_INSTANCE = LogService.class.getName() + ".initialServiceInstance";

    public void logRequest(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI forwardUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        ServiceInstance serviceInstance = resolveServiceInstance(exchange);

        if (forwardUrl != null) {
            exchange.getAttributes().put(ATTR_INITIAL_FORWARD_URL, forwardUrl);
        }
        if (serviceInstance != null) {
            exchange.getAttributes().put(ATTR_INITIAL_SERVICE_INSTANCE, serviceInstance);
        }

        ServerHttpRequest request = exchange.getRequest();
        String routeId = resolveRouteId(route);
        String targetUri = resolveTargetUri(route, forwardUrl);
        String instanceDesc = formatServiceInstance(serviceInstance);
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";

        OpenLog.info(log, GatewayEventEnum.REQUEST,
                "method", method,
                "uri", request.getURI(),
                "routeId", routeId,
                "target", targetUri,
                "instance", instanceDesc);

        request.getHeaders().forEach((name, values) -> logHeader("GatewayRequestHeader", "Request header", name, values));
        if (route != null) {
            OpenLog.debug(log, GatewayEventEnum.ROUTE,
                    "id", route.getId(),
                    "uri", route.getUri(),
                    "metadata", route.getMetadata());
        }
        if (forwardUrl != null) {
            OpenLog.debug(log, GatewayEventEnum.FORWARD_URL, "url", forwardUrl);
        }
        if (serviceInstance != null) {
            OpenLog.debug(log, GatewayEventEnum.SERVICE_INSTANCE,
                    "serviceId", serviceInstance.getServiceId(),
                    "host", serviceInstance.getHost(),
                    "port", serviceInstance.getPort());
        } else {
            OpenLog.debug(log, GatewayEventEnum.SERVICE_INSTANCE_PENDING,
                    "routeId", routeId);
        }
    }

    public void logResponse(ServerWebExchange exchange, long durationMs) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI initialForwardUrl = exchange.getAttribute(ATTR_INITIAL_FORWARD_URL);
        ServiceInstance initialInstance = exchange.getAttribute(ATTR_INITIAL_SERVICE_INSTANCE);
        URI finalForwardUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        ServiceInstance finalInstance = resolveServiceInstance(exchange);

        String routeId = resolveRouteId(route);
        String targetUri = resolveTargetUri(route, finalForwardUrl);
        String finalInstanceDesc = formatServiceInstance(finalInstance);

        ServerHttpResponse response = exchange.getResponse();

        OpenLog.info(log, GatewayEventEnum.RESPONSE,
                "status", response.getStatusCode(),
                "durationMs", durationMs,
                "routeId", routeId,
                "target", targetUri,
                "instance", finalInstanceDesc);

        if (finalForwardUrl != null && initialForwardUrl == null) {
            OpenLog.debug(log, GatewayEventEnum.FORWARD_URL_RESOLVED, "url", finalForwardUrl);
        }
        if (finalInstance != null && initialInstance == null) {
            OpenLog.debug(log, GatewayEventEnum.SERVICE_INSTANCE_RESOLVED,
                    "serviceId", finalInstance.getServiceId(),
                    "host", finalInstance.getHost(),
                    "port", finalInstance.getPort());
        }

        response.getHeaders().forEach((name, values) -> logHeader("GatewayResponseHeader", "Response header", name, values));
    }

    public void logForwardFailure(ServerWebExchange exchange, Throwable ex) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI forwardUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);

        String routeId = resolveRouteId(route);
        String targetUri = resolveTargetUri(route, forwardUrl);

        OpenLog.error(log, GatewayEventEnum.FORWARD_FAILED, ex,
                "routeId", routeId,
                "target", targetUri);
    }

    private void logHeader(String event, String message, String name, List<String> values) {
        GatewayEventEnum evt = "GatewayResponseHeader".equals(event) ? GatewayEventEnum.RESPONSE_HEADER : GatewayEventEnum.REQUEST_HEADER;
        OpenLog.debug(log, evt, "name", name, "values", values);
    }

    private String resolveRouteId(Route route) {
        return route != null ? route.getId() : "unknown";
    }

    private String resolveTargetUri(Route route, URI forwardUrl) {
        if (forwardUrl != null) {
            return forwardUrl.toString();
        }
        if (route != null && route.getUri() != null) {
            return route.getUri().toString();
        }
        return "unknown";
    }

    private ServiceInstance resolveServiceInstance(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR);
        if (attr instanceof ServiceInstance) {
            return (ServiceInstance) attr;
        }
        if (attr instanceof Response<?>) {
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
        StringBuilder sb = new StringBuilder();
        sb.append(serviceId != null ? serviceId : "unknown-service");
        sb.append('@');
        sb.append(host != null ? host : "unknown-host");
        sb.append(':').append(port);
        sb.append('(').append(scheme).append(')');
        return sb.toString();
    }
}
