package com.mykart.order.service;

import com.mykart.order.entity.OutboxEvent;
import com.mykart.order.enums.SagaStep;
import com.mykart.order.repository.OutboxEventRepository;
import com.mykart.order.repository.SagaStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxEventRepository outboxEventRepository;
    private final SagaStateRepository sagaStateRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxEventRepository outboxEventRepository,
                        SagaStateRepository sagaStateRepository,
                        KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void poll() {
        List<OutboxEvent> unpublished = outboxEventRepository
                .findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(50));

        if (unpublished.isEmpty()) return;

        log.debug("OutboxPoller: found {} unpublished events", unpublished.size());

        for (OutboxEvent event : unpublished) {
            try {
                String topic = topicFor(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);

                if ("ORDER_CONFIRMED".equals(event.getEventType())) {
                    sagaStateRepository.findById(event.getAggregateId()).ifPresent(saga -> {
                        saga.setStep(SagaStep.COMPLETED);
                        sagaStateRepository.save(saga);
                    });
                }
                log.info("Outbox event published: topic={} orderId={}", topic, event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} — will retry next poll", event.getId(), e);
            }
        }
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "ORDER_CONFIRMED" -> "order.confirmed";
            case "ORDER_CANCELLED" -> "order.cancelled";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
