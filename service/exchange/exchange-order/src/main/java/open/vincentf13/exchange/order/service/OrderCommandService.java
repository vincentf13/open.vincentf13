package open.vincentf13.exchange.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.infra.OrderEvent;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderEventRepository;
import open.vincentf13.exchange.order.mq.event.FundsFreezeRequestedEvent;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.position.sdk.rest.client.ExchangePositionClient;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckRequest;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckResponse;
import open.vincentf13.exchange.risk.sdk.rest.client.ExchangeRiskClient;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserHolder;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Validated
public class OrderCommandService {
    
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderEventRepository orderEventRepository;
    private final ExchangePositionClient exchangePositionClient;
    private final ExchangeRiskClient exchangeRiskClient;
    private final TransactionTemplate transactionTemplate;
    
    public OrderResponse createOrder(@Valid OrderCreateRequest request) {
        Long userId = currentUserId();
        try {
            Order order = Order.createNew(userId, request);
            PositionIntentResponse intentResponse = OpenApiClientInvoker.call(
                    () -> exchangePositionClient.prepareIntent(new PositionIntentRequest(userId, request.instrumentId(), toPositionSide(request.side()), request.quantity())),
                    msg -> OpenException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                                            Map.of("userId", userId, "instrumentId", request.instrumentId(), "remoteMessage", msg))
                                                                             );
            PositionIntentType intentType = intentResponse.intentType();
            order.setIntent(intentType);
            if (intentResponse.intentType() == null) {
                return rejectOrder(order, "intentMissing");
            }
            if (intentResponse.rejectReason() != null) {
                return rejectOrder(order, intentResponse.rejectReason());
            }
            
            
            OrderPrecheckResponse precheck = precheckOrder(userId, order, intentType, intentResponse.positionSnapshot());
            if (!precheck.isAllow()) {
                return rejectOrder(order, Optional.ofNullable(precheck.getReason()).orElse("riskRejected"));
            }
            
            if (intentType == PositionIntentType.INCREASE) {
                order.setStatus(OrderStatus.FREEZING_MARGIN);
                Instant eventTime = Instant.now();
                transactionTemplate.executeWithoutResult(status -> {
                    orderRepository.insertSelective(order);
                    recordOrderEvent(order,
                                     "ORDER_CREATED",
                                     actorFromUser(userId),
                                     Instant.now(),
                                     orderCreatedPayload(order),
                                     "REQUEST",
                                     null);
                    orderEventPublisher.publishFundsFreezeRequested(new FundsFreezeRequestedEvent(
                            order.getOrderId(),
                            userId,
                            request.instrumentId(),
                            precheck.getRequiredMargin(),
                            precheck.getFee(),
                            eventTime
                    ));
                });
            } else {
                order.setStatus(OrderStatus.NEW);
                Instant submittedAt = Instant.now();
                order.setSubmittedAt(submittedAt);
                transactionTemplate.executeWithoutResult(status -> {
                    orderRepository.insertSelective(order);
                    recordOrderEvent(order,
                                     "ORDER_CREATED",
                                     actorFromUser(userId),
                                     submittedAt,
                                     orderCreatedPayload(order),
                                     "REQUEST",
                                     null);
                    orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                            order.getOrderId(),
                            userId,
                            request.instrumentId(),
                            request.side(),
                            request.type(),
                            order.getIntent(),
                            order.getPrice(),
                            order.getQuantity(),
                            order.getClientOrderId(),
                            submittedAt
                    ));
                });
            }
            
            return loadPersistedOrder(order.getOrderId())
                           .map(o -> OpenObjectMapper.convert(o, OrderResponse.class))
                           .orElse(OpenObjectMapper.convert(order, OrderResponse.class));
        } catch (DuplicateKeyException ex) {
            OpenLog.info(OrderEvent.ORDER_DUPLICATE_INSERT,
                         "userId", userId,
                         "clientOrderId", request.clientOrderId());
            return orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery()
                                                   .eq(OrderPO::getUserId, userId)
                                                   .eq(OrderPO::getClientOrderId, request.clientOrderId()))
                                  .map(o -> OpenObjectMapper.convert(o, OrderResponse.class))
                                  .orElseThrow(() -> OpenException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                                                                      Map.of("userId", userId, "clientOrderId", request.clientOrderId())));
        }
    }
    
    private Long currentUserId() {
        return OpenJwtLoginUserHolder.currentUserIdOrThrow(() ->
                                                                   OpenException.of(OrderErrorCode.ORDER_NOT_FOUND));
    }
    
    private OrderPrecheckResponse precheckOrder(Long userId,
                                                Order order,
                                                PositionIntentType intentType,
                                                PositionResponse position) {
        OrderPrecheckRequest precheckRequest = new OrderPrecheckRequest();
        precheckRequest.setUserId(userId);
        precheckRequest.setInstrumentId(order.getInstrumentId());
        precheckRequest.setSide(order.getSide());
        precheckRequest.setType(order.getType());
        precheckRequest.setPrice(order.getPrice());
        precheckRequest.setQuantity(order.getQuantity());
        precheckRequest.setIntent(intentType);
        OrderPrecheckRequest.PositionSnapshot snapshot = new OrderPrecheckRequest.PositionSnapshot();
        if (position != null) {
            snapshot.setLeverage(position.leverage());
            snapshot.setMargin(position.margin());
            snapshot.setQuantity(position.quantity());
            snapshot.setMarkPrice(position.markPrice());
            snapshot.setUnrealizedPnl(position.unrealizedPnl());
        }
        precheckRequest.setPositionSnapshot(snapshot);
        return OpenApiClientInvoker.call(
                () -> exchangeRiskClient.precheckOrder(precheckRequest),
                msg -> OpenException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                                        Map.of("instrumentId", order.getInstrumentId(), "reason", msg))
                                        );
    }
    
    private Optional<Order> loadPersistedOrder(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery()
                                               .eq(OrderPO::getOrderId, orderId));
    }
    
    private PositionSide toPositionSide(OrderSide orderSide) {
        if (orderSide == null) {
            return null;
        }
        return orderSide == OrderSide.BUY
               ? PositionSide.LONG
               : PositionSide.SHORT;
    }
    
    private OrderResponse rejectOrder(Order order,
                                      String reason) {
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectedReason(reason);
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.insertSelective(order);
            recordOrderEvent(order,
                             "ORDER_REJECTED",
                             actorFromUser(order.getUserId()),
                             Instant.now(),
                             Map.of("reason", reason),
                             "RISK_CHECK",
                             null);
        });
        return loadPersistedOrder(order.getOrderId())
                       .map(o -> OpenObjectMapper.convert(o, OrderResponse.class))
                       .orElse(OpenObjectMapper.convert(order, OrderResponse.class));
    }
    
    private String actorFromUser(Long userId) {
        return "USER:" + userId;
    }

    private Map<String, Object> orderCreatedPayload(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        payload.put("userId", order.getUserId());
        payload.put("instrumentId", order.getInstrumentId());
        payload.put("side", order.getSide() != null ? order.getSide().name() : null);
        payload.put("type", order.getType() != null ? order.getType().name() : null);
        payload.put("price", order.getPrice());
        payload.put("quantity", order.getQuantity());
        payload.put("intent", order.getIntent() != null ? order.getIntent().name() : null);
        payload.put("clientOrderId", order.getClientOrderId());
        payload.put("submittedAt", order.getSubmittedAt());
        return payload;
    }

    private void recordOrderEvent(Order order,
                                  String eventType,
                                  String actor,
                                  Instant occurredAt,
                                  Object payload,
                                  String referenceType,
                                  Long referenceId) {
        orderEventRepository.append(order, eventType, actor, occurredAt, payload, referenceType, referenceId);
    }
}
