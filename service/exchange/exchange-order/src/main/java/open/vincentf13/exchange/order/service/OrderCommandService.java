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
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.client.ExchangePositionClient;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
@Validated
public class OrderCommandService {
    
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ExchangePositionClient exchangePositionClient;
    private final TransactionTemplate transactionTemplate;
    
    public OrderResponse createOrder(@Valid OrderCreateRequest request) {
        Long userId = currentUserId();
        try {
            Order order = Order.createNew(userId, request);
            PositionIntentType intentType = determineIntent(userId, request);
            order.setIntent(intentType);
            // TODO 平倉寫入寫入entry price
            
            transactionTemplate.executeWithoutResult(status -> {
                if (order.getIntent() != null && order.getIntent().requiresPositionReservation()) {
                    orderEventPublisher.publishPositionReserveRequested(order, order.getIntent());
                } else {
                    markSubmitted(order);
                    orderEventPublisher.publishOrderSubmitted(order);
                }
                orderRepository.insertSelective(order);
            });
            
            return OpenObjectMapper.convert(order, OrderResponse.class);
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
    
    private PositionIntentType determineIntent(Long userId,
                                               OrderCreateRequest request) {
        PositionIntentResponse response = OpenApiClientInvoker.call(
                () -> exchangePositionClient.determineIntent(new PositionIntentRequest(userId, request.instrumentId(), toPositionSide(request.side()), request.quantity())),
                msg -> OpenException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                                        Map.of("userId", userId, "instrumentId", request.instrumentId(), "remoteMessage", msg))
                                                                   );
        PositionIntentType intentType = response.intentType();
        if (intentType == null) {
            throw OpenException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                                   Map.of("instrumentId", request.instrumentId()));
        }
        return intentType;
    }
    
    private PositionSide toPositionSide(OrderSide orderSide) {
        if (orderSide == null) {
            return null;
        }
        return orderSide == OrderSide.BUY
               ? PositionSide.LONG
               : PositionSide.SHORT;
    }
    
    private void markSubmitted(Order order) {
        Instant now = Instant.now();
        order.markStatus(OrderStatus.SUBMITTED, now);
        order.incrementVersion();
    }
}
