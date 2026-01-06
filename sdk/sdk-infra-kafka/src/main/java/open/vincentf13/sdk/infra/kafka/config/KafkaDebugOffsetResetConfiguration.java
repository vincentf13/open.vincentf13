package open.vincentf13.sdk.infra.kafka.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;

import java.util.Collection;

@Configuration
public class KafkaDebugOffsetResetConfiguration implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ConcurrentKafkaListenerContainerFactory) {
            ConcurrentKafkaListenerContainerFactory<?, ?> factory = (ConcurrentKafkaListenerContainerFactory<?, ?>) bean;
            factory.getContainerProperties().setConsumerRebalanceListener(new ConsumerAwareRebalanceListener() {
                @Override
                public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
                    // 調試用
                    consumer.seekToBeginning(partitions);
                }
            });
        }
        return bean;
    }
}
