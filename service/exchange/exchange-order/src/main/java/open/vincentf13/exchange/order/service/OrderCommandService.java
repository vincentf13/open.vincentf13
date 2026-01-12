package open.vincentf13.exchange.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.*;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.infra.OrderEvent;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderEventRepository;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.mq.event.FundsFreezeRequestedEvent;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventReferenceType;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventType;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.position.sdk.rest.client.ExchangePositionClient;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckRequest;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckResponse;
import open.vincentf13.exchange.risk.sdk.rest.client.ExchangeRiskClient;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.OpenObjectDiff;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

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
    private final RetryTaskRepository pendingTaskRepository;
    
    private static final String ACTOR_ACCOUNT = "ACCOUNT_SERVICE";
    private static final String ACTOR_MATCHING = "MATCHING_ENGINE";
    private static final Set<OrderStatus> UPDATABLE_STATUSES =
            EnumSet.of(OrderStatus.NEW,
                       OrderStatus.PARTIALLY_FILLED,
                       OrderStatus.FILLED,
                       OrderStatus.CANCELLING,
                       OrderStatus.CANCELLED);
    
    public void processFundsFrozen(Long orderId,
                                   Instant eventTime) {
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery().eq(OrderPO::getOrderId, orderId))
                                         .orElse(null);
            if (order == null) {
                OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE, Map.of("orderId", orderId));
                return;
            }
            
            Order originalOrder = OpenObjectMapper.fromJson(OpenObjectMapper.toJson(order), Order.class);
            int expectedVersion = order.getVersion() == null ? 0 : order.getVersion();
            OrderStatus originalStatus = order.getStatus();
            
            order.onFundsFrozen(eventTime);
            
            if (order.getStatus() == originalStatus) {
                return;
            }
            
            order.incrementVersion();
            
            boolean updated = orderRepository.updateSelective(
                    order,
                    Wrappers.<OrderPO>lambdaUpdate()
                            .eq(OrderPO::getOrderId, orderId)
                            .eq(OrderPO::getStatus, originalStatus)
                            .eq(OrderPO::getVersion, expectedVersion));
            
            if (!updated) {
                OpenLog.warn(OrderEvent.ORDER_FAILURE_OPTIMISTIC_LOCK, Map.of("orderId", orderId));
                status.setRollbackOnly();
                return;
            }
            
            orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                    order.getOrderId(),
                    order.getUserId(),
                    order.getInstrumentId(),
                    order.getSide(),
                    order.getType(),
                    order.getIntent(),
                    TradeType.NORMAL,
                    order.getPrice(),
                    order.getQuantity(),
                    order.getClientOrderId(),
                    order.getSubmittedAt()
            ));
            
            String payload = OpenObjectDiff.diff(originalOrder, order);
            
            orderEventRepository.append(order, OrderEventType.ORDER_SUBMITTED, ACTOR_ACCOUNT, eventTime, payload, OrderEventReferenceType.ACCOUNT_EVENT, null);
        });
    }
    
        public void processFundsFreezeFailed(Long orderId,
                                             String reason,
                                             Instant eventTime) {
            transactionTemplate.executeWithoutResult(status -> {
                Order order = orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery().eq(OrderPO::getOrderId, orderId))
                                             .orElse(null);
                if (order == null) {
                    OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE, Map.of("orderId", orderId));
                    return;
                }
                
                Order originalOrder = OpenObjectMapper.fromJson(OpenObjectMapper.toJson(order), Order.class);
                int expectedVersion = order.getVersion() == null ? 0 : order.getVersion();
                OrderStatus originalStatus = order.getStatus();
    
                order.onFundsFreezeFailed(reason, eventTime);
    
                if (order.getStatus() == originalStatus) {
                    return;
                }
    
                order.incrementVersion();
    
                boolean updated = orderRepository.updateSelective(
                        order,
                        Wrappers.<OrderPO>lambdaUpdate()
                                .eq(OrderPO::getOrderId, orderId)
                                .eq(OrderPO::getStatus, originalStatus)
                                .eq(OrderPO::getVersion, expectedVersion));
    
                if (!updated) {
                    OpenLog.warn(OrderEvent.ORDER_FAILURE_OPTIMISTIC_LOCK, Map.of("orderId", orderId));
                    status.setRollbackOnly();
                    return;
                }
                
                String payload = OpenObjectDiff.diff(originalOrder, order);
                
                orderEventRepository.append(order, OrderEventType.ORDER_REJECTED, ACTOR_ACCOUNT, eventTime, payload, OrderEventReferenceType.ACCOUNT_EVENT, null);
            });
        }    
    public OrderResponse createOrder(@NotNull Long userId, @Valid OrderCreateRequest request) {
        if (request.getClientOrderId() == null || request.getClientOrderId().trim().isEmpty()) {
            request.setClientOrderId(UUID.randomUUID().toString());
        }
        
        try {
            Order order = Order.createNew(userId, request);
            
            PositionIntentResponse intentResponse = OpenApiClientInvoker.call(
                    () -> exchangePositionClient.prepareIntent(new PositionIntentRequest(userId, request.getInstrumentId(), toPositionSide(request.getSide()), request.getQuantity(), request.getClientOrderId())),
                    msg -> OpenException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                                            Map.of("userId", userId, "instrumentId", request.getInstrumentId(), "remoteMessage", msg))
                                                                             );
            PositionIntentType intentType = intentResponse.intentType();
            
            if (intentType == null) {
                return rejectOrder(order, "intentMissing");
            }
            if (intentResponse.rejectReason() != null) {
                return rejectOrder(order, intentResponse.rejectReason());
            }
            
            OrderPrecheckResponse precheck = precheckOrder(userId, order, intentType, intentResponse.positionSnapshot());
            if (!precheck.isAllow()) {
                return rejectOrder(order, Optional.ofNullable(precheck.getReason()).orElse("riskRejected"));
            }
            
            order.onPositionIntentDetermined(intentType);
            
            if (intentType == PositionIntentType.INCREASE) {
                Instant eventTime = Instant.now();
                transactionTemplate.executeWithoutResult(status -> {
                    orderRepository.insertSelective(order);
                    Instant occurredAt = Instant.now();
                    String payload = OpenObjectDiff.diff(null, order);
                    orderEventRepository.append(order, OrderEventType.ORDER_CREATED, actorFromUser(userId), occurredAt, payload, OrderEventReferenceType.REQUEST, null);
                    orderEventPublisher.publishFundsFreezeRequested(new FundsFreezeRequestedEvent(
                            order.getOrderId(),
                            userId,
                            request.getInstrumentId(),
                            precheck.getRequiredMargin(),
                            precheck.getFee(),
                            eventTime
                    ));
                });
            } else {
                transactionTemplate.executeWithoutResult(status -> {
                    orderRepository.insertSelective(order);
                    String payload = OpenObjectDiff.diff(null, order);
                    orderEventRepository.append(order, OrderEventType.ORDER_CREATED, actorFromUser(userId), order.getSubmittedAt(), payload, OrderEventReferenceType.REQUEST, null);
                    orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                            order.getOrderId(),
                            userId,
                            request.getInstrumentId(),
                            request.getSide(),
                            request.getType(),
                            order.getIntent(),
                            TradeType.NORMAL,
                            order.getPrice(),
                            order.getQuantity(),
                            order.getClientOrderId(),
                            order.getSubmittedAt()
                    ));
                });
            }
            
            return loadPersistedOrder(order.getOrderId())
                           .map(o -> OpenObjectMapper.convert(o, OrderResponse.class))
                           .orElse(OpenObjectMapper.convert(order, OrderResponse.class));
        } catch (DuplicateKeyException ex) {
            OpenLog.info(OrderEvent.ORDER_DUPLICATE_INSERT,
                         "userId", userId,
                         "clientOrderId", request.getClientOrderId());
            return orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery()
                                                   .eq(OrderPO::getUserId, userId)
                                                   .eq(OrderPO::getClientOrderId, request.getClientOrderId()))
                                  .map(o -> OpenObjectMapper.convert(o, OrderResponse.class))
                                  .orElseThrow(() -> OpenException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                                                                      Map.of("userId", userId, "clientOrderId", request.getClientOrderId())));
        }
    }
    
    public void processTradeExecution(Long orderId,
                                      Long tradeId,
                                      BigDecimal price,
                                      BigDecimal filledQuantity,
                                      BigDecimal feeDelta,
                                      Instant executedAt) {
        transactionTemplate.executeWithoutResult(status -> {
            if (orderEventRepository.existsByReference(orderId, OrderEventReferenceType.TRADE, String.valueOf(tradeId))) {
                return;
            }
            
            Order order = orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery().eq(OrderPO::getOrderId, orderId))
                                         .orElse(null);
            
                        if (order == null) {
                            OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE,
                                         Map.of("orderId", orderId, "tradeId", tradeId));
                            return;
                        }
                        
                        if (!UPDATABLE_STATUSES.contains(order.getStatus())) {
                            return;
                        }
            
                        Order originalOrder = OpenObjectMapper.fromJson(OpenObjectMapper.toJson(order), Order.class);
                        int expectedVersion = order.getVersion() == null ? 0 : order.getVersion();
                        
                        order.onTradeExecuted(price, filledQuantity, feeDelta, executedAt);
                        
                        order.incrementVersion();
            
                        boolean updated = orderRepository.updateSelective(
                                order,
                                Wrappers.<OrderPO>lambdaUpdate()
                                        .eq(OrderPO::getOrderId, orderId)
                                        .eq(OrderPO::getVersion, expectedVersion));
            
                        if (!updated) {
                            OpenLog.warn(OrderEvent.ORDER_FAILURE_OPTIMISTIC_LOCK,
                                         Map.of("orderId", orderId, "tradeId", tradeId));
                            status.setRollbackOnly();
                            return;
                        }
                        
                        String payload = OpenObjectDiff.diff(originalOrder, order);
                        
                        orderEventRepository.append(order, OrderEventType.ORDER_TRADE_FILLED, ACTOR_MATCHING, executedAt, payload, OrderEventReferenceType.TRADE, String.valueOf(tradeId));
                    });
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
    
    private static final int MAX_REJECTED_REASON_LENGTH = 255;
    
    private OrderResponse rejectOrder(Order order,
                                      String reason) {
        String truncatedReason = reason != null && reason.length() > MAX_REJECTED_REASON_LENGTH
                                 ? reason.substring(0, MAX_REJECTED_REASON_LENGTH)
                                 : reason;
        
        Order originalOrder = OpenObjectMapper.fromJson(OpenObjectMapper.toJson(order), Order.class);
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectedReason(truncatedReason);
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.insertSelective(order);
            Instant occurredAt = Instant.now();
            String payload = OpenObjectDiff.diff(originalOrder, order);
            orderEventRepository.append(order, OrderEventType.ORDER_REJECTED, actorFromUser(order.getUserId()), occurredAt, payload, OrderEventReferenceType.RISK_CHECK, null);
        });
        return loadPersistedOrder(order.getOrderId())
                       .map(o -> OpenObjectMapper.convert(o, OrderResponse.class))
                       .orElse(OpenObjectMapper.convert(order, OrderResponse.class));
    }
    
    private String actorFromUser(Long userId) {
        return "USER:" + userId;
    }
}
