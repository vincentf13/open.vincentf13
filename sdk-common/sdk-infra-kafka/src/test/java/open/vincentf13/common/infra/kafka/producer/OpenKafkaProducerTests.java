package open.vincentf13.common.infra.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenKafkaProducerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void sendSerializesPayloadAndHeaders() throws Exception {
        KafkaTemplate<String, byte[]> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        AtomicReference<ProducerRecord<String, byte[]>> capturedRecord = new AtomicReference<>();
        CompletableFuture<SendResult<String, byte[]>> completableFuture = new CompletableFuture<>();
        Mockito.when(kafkaTemplate.send(Mockito.<ProducerRecord<String, byte[]>>any())).thenAnswer(invocation -> {
            ProducerRecord<String, byte[]> record = invocation.getArgument(0);
            capturedRecord.set(record);
            return completableFuture;
        });

        OpenKafkaProducer.initialize(kafkaTemplate, objectMapper);

        Map<String, Object> message = Map.of("orderId", "O-1", "userId", "U-9");
        Map<String, Object> headers = Map.of("source", "api", "attempt", 1);

        CompletableFuture<SendResult<String, byte[]>> resultFuture =
                OpenKafkaProducer.send(
                        "orders",
                        (String) message.get("orderId"),
                        message,
                        headers
                );

        ProducerRecord<String, byte[]> sentRecord = capturedRecord.get();
        completableFuture.complete(new SendResult<>(sentRecord, null));

        SendResult<String, byte[]> result = resultFuture.join();
        assertEquals("orders", sentRecord.topic());
        assertEquals("O-1", sentRecord.key());
        assertArrayEquals(objectMapper.writeValueAsBytes(message), sentRecord.value());

        byte[] sourceHeader = sentRecord.headers().lastHeader("source").value();
        byte[] attemptHeader = sentRecord.headers().lastHeader("attempt").value();
        assertEquals("api", new String(sourceHeader, StandardCharsets.UTF_8));
        assertEquals(1, objectMapper.readValue(attemptHeader, Integer.class));
        assertEquals(sentRecord, result.getProducerRecord());
    }

    @Test
    void sendCompletesExceptionallyWhenSerializationFails() throws Exception {
        KafkaTemplate<String, byte[]> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        ObjectMapper failingMapper = Mockito.mock(ObjectMapper.class);
        Mockito.doThrow(new RuntimeException("fail")).when(failingMapper).writeValueAsBytes(Mockito.any());

        OpenKafkaProducer.initialize(kafkaTemplate, failingMapper);

        CompletableFuture<SendResult<String, byte[]>> future =
                OpenKafkaProducer.send("topic", Map.of());

        assertTrue(future.isCompletedExceptionally());
        assertThrows(RuntimeException.class, future::join);
    }

    @Test
    void sendBatchAggregatesResults() {
        KafkaTemplate<String, byte[]> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        List<ProducerRecord<String, byte[]>> records = new ArrayList<>();
        List<CompletableFuture<SendResult<String, byte[]>>> futures = new ArrayList<>();
        Mockito.when(kafkaTemplate.send(Mockito.<ProducerRecord<String, byte[]>>any())).thenAnswer(invocation -> {
            ProducerRecord<String, byte[]> record = invocation.getArgument(0);
            records.add(record);
            CompletableFuture<SendResult<String, byte[]>> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        });

        OpenKafkaProducer.initialize(kafkaTemplate, objectMapper);

        List<Map<String, Object>> payloads = List.of(Map.of("id", 1), Map.of("id", 2));
        CompletableFuture<List<SendResult<String, byte[]>>> batchFuture = OpenKafkaProducer.sendBatch(
                "batch",
                payloads,
                payload -> "key-" + ((Map<?, ?>) payload).get("id"),
                payload -> Map.of("attempt", 1)
        );

        for (int i = 0; i < futures.size(); i++) {
            futures.get(i).complete(new SendResult<>(records.get(i), null));
        }

        List<SendResult<String, byte[]>> results = batchFuture.join();
        assertEquals(2, results.size());
        assertEquals("batch", records.getFirst().topic());
        assertEquals("key-1", records.getFirst().key());
    }
}
