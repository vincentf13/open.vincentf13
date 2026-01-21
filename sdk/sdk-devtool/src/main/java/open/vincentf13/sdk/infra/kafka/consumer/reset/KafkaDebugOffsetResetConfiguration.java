package open.vincentf13.sdk.infra.kafka.consumer.reset;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaDebugOffsetResetConfiguration implements BeanPostProcessor {

  // @Override
  // public Object postProcessAfterInitialization(Object bean, String beanName) throws
  // BeansException {
  //    if (bean instanceof ConcurrentKafkaListenerContainerFactory) {
  //        ConcurrentKafkaListenerContainerFactory<?, ?> factory =
  // (ConcurrentKafkaListenerContainerFactory<?, ?>) bean;
  //        factory.getContainerProperties().setConsumerRebalanceListener(new
  // ConsumerAwareRebalanceListener() {
  //            @Override
  //            public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition>
  // partitions) {
  //                // 調試用
  //                consumer.seekToBeginning(partitions);
  //            }
  //        });
  //    }
  //    return bean;
  // }
}
