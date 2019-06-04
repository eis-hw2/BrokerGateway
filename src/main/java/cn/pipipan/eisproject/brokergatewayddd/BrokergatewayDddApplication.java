package cn.pipipan.eisproject.brokergatewayddd;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.spring.eventsourcing.SpringAggregateSnapshotter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SpringBootApplication
@Cacheable
public class BrokergatewayDddApplication {
    public static ApplicationContext ac;
    @Bean
    public SpringAggregateSnapshotter snapshotter(
                                                  EventStore eventStore,
                                                  TransactionManager transactionManager) {
        Executor executor = Executors.newSingleThreadExecutor();
        return new SpringAggregateSnapshotter.Builder().eventStore(eventStore).executor(executor).transactionManager(transactionManager).build();
    }
    @Bean
    public SnapshotTriggerDefinition mySnapshotTriggerDefinition(Snapshotter snapshotter) {
        return new EventCountSnapshotTriggerDefinition(snapshotter, 500);
    }

    @Bean
    public EventGateway eventGateway(EventStore eventStore){
        return new DefaultEventGateway.Builder().eventBus(eventStore).build();
    }

    public static void main(String[] args) {
        ac = SpringApplication.run(BrokergatewayDddApplication.class, args);
    }

}
