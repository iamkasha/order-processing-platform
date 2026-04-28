package com.kasha.orderprocessing;

import com.kasha.orderprocessing.kafka.OrderEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderProcessingApplicationTests {

    @MockBean
    KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Test
    void contextLoads() {
    }
}
