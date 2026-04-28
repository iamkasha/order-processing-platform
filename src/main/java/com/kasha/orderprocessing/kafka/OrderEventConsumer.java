package com.kasha.orderprocessing.kafka;

import com.kasha.orderprocessing.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventConsumer {

    @KafkaListener(topics = KafkaConfig.ORDER_EVENTS_TOPIC, groupId = "order-group")
    public void consume(OrderEvent event) {
        log.info("Received order event: type={}, orderId={}, status={}, amount={}",
                event.getEventType(), event.getOrderId(), event.getStatus(), event.getTotalAmount());

        // Extension point: send confirmation email, notify inventory service, trigger analytics, etc.
    }
}
