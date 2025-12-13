package open.vincentf13.exchange.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.*;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.infra.OrderEvent;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Validated
public class OrderCommandService {
    
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
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
            if (intentResponse.intentType() == null) {
                return rejectOrder(order, "intentMissing");
            }
            if (intentResponse.rejectReason() != null) {
                return rejectOrder(order, intentResponse.rejectReason());
            }
            
            PositionIntentType intentType = intentResponse.intentType();
            order.setIntent(intentType);
            Instant now = Instant.now();
            
            PositionResponse positionSnapshot = intentResponse.positionSnapshot();

            if (intentType == PositionIntentType.INCREASE) {
                OrderPrecheckResponse precheck = precheckOrder(userId, order, intentType, positionSnapshot);
                if (!precheck.isAllow()) {
                    return rejectOrder(order,
                                       Optional.ofNullable(precheck.getReason()).orElse("riskRejected"));
                }
                BigDecimal fee = Optional.ofNullable(precheck.getFee()).orElse(BigDecimal.ZERO);
                BigDecimal requiredMargin = Optional.ofNullable(precheck.getRequiredMargin()).orElse(BigDecimal.ZERO);
                order.setFee(fee);
                order.setStatus(OrderStatus.FREEZING_MARGIN);
                Instant eventTime = now;
                transactionTemplate.executeWithoutResult(status -> {
                    orderRepository.insertSelective(order);
                    orderEventPublisher.publishFundsFreezeRequested(new FundsFreezeRequestedEvent(
                            order.getOrderId(),
                            userId,
                            request.instrumentId(),
                            requiredMargin,
                            fee,
                            eventTime
                    ));
                });
            } else {
                order.setStatus(OrderStatus.NEW);
                Instant submittedAt = now;
                order.setSubmittedAt(submittedAt);
                transactionTemplate.executeWithoutResult(status -> {
                    orderRepository.insertSelective(order);
                    orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                            order.getOrderId(),
                            userId,
                            request.instrumentId(),
                            request.side(),
                            request.type(),
                            order.getPrice(),
                            order.getQuantity(),
                            order.getClientOrderId(),
                            AssetSymbol.UNKNOWN,
                            BigDecimal.ZERO,
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
        transactionTemplate.executeWithoutResult(status -> orderRepository.insertSelective(order));
        return loadPersistedOrder(order.getOrderId())
                .map(o -> OpenObjectMapper.convert(o, OrderResponse.class))
                .orElse(OpenObjectMapper.convert(order, OrderResponse.class));
    }
  
}
