package com.example.rabbitqueueautoscaler;

import com.rabbitmq.client.AMQP;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.ScaleApplicationRequest;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.cloudfoundry.reactor.uaa.ReactorUaaClient;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.GenericSelector;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.transformer.Transformer;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class RabbitQueueAutoscalerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitQueueAutoscalerApplication.class, args);
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class QueueStatistics {

    private String queue;

    private int size, consumers;
}

@Slf4j
@Configuration
class ScaleIntegration {

    private final RabbitOperations rabbitOperations;
    private final CloudFoundryOperations client;
    private String applicationName, queueName;

    ScaleIntegration(
            @Value("${autoscaler.rabbitMq.queueName}") String q,
            @Value("${autoscaler.rabbitMq.applicationName}") String appName,
            RabbitOperations rabbitOperations,
            CloudFoundryOperations client) {
        this.rabbitOperations = rabbitOperations;
        this.queueName = q;
        this.applicationName = appName;
        this.client = client;
    }

    @Bean
    IntegrationFlow scaler() {

        MessageSource<QueueStatistics> qms = () ->
                MessageBuilder.withPayload(queueStatistics(queueName)).build();

        return IntegrationFlows
                .from(qms, sp -> sp.poller(p -> p.fixedRate(1000)))
                .transform((Transformer) message -> {
                    Object payload = message.getPayload();
                    QueueStatistics statistics = QueueStatistics.class.cast(payload);

                    int consumers = statistics.getConsumers();
                    int size = statistics.getSize();

                    Map<String, Object> statsMap = new HashMap<>();
                    statsMap.put("queue-name", statistics.getQueue());
                    statsMap.put("queue-size", size);
                    statsMap.put("queue-consumers", consumers);
                    statsMap.put("application-name", this.applicationName);

                    int average = 0;
                    if (size > 0 && consumers > 0)
                        average = size / consumers;

                    return MessageBuilder
                            .withPayload(average)
                            .copyHeadersIfAbsent(message.getHeaders()).copyHeadersIfAbsent(statsMap)
                            .build();
                })
                .filter((GenericSelector<Integer>) average -> {
                    return average > 5; // this means that there should never be more than 5 records deep in the queue.
                })
                .handle(message ->
                        client.applications()
                                .get(GetApplicationRequest.builder().name(this.applicationName).build())
                                .map(ApplicationDetail::getRunningInstances)
                                .flatMap(instances ->
                                        client
                                                .applications()
                                                .scale(ScaleApplicationRequest.builder().name(this.applicationName).instances(instances + 1).build()))
                                .subscribe(x -> log.info("scaled " + this.applicationName + ".")))
                .get();
    }

    private QueueStatistics queueStatistics(String q) {
        return this.rabbitOperations.execute(channel -> {
            AMQP.Queue.DeclareOk queueInfo = channel.queueDeclarePassive(q);
            return new QueueStatistics(q, queueInfo.getMessageCount(), queueInfo
                    .getConsumerCount());
        });
    }
}

@Configuration
class CloudFoundryOperationsConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactorCloudFoundryClient cloudFoundryClient(
            ConnectionContext connectionContext,
            TokenProvider tokenProvider) {
        return ReactorCloudFoundryClient.builder()
                .connectionContext(connectionContext).tokenProvider(tokenProvider).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactorDopplerClient dopplerClient(ConnectionContext connectionContext,
                                              TokenProvider tokenProvider) {
        return ReactorDopplerClient.builder().connectionContext(connectionContext)
                .tokenProvider(tokenProvider).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultConnectionContext connectionContext(
            @Value("${cf.api}") String apiHost,
            @Value("${cf.skip-ssl-validation:false}") boolean skipSsl) {
        if (apiHost.contains("://")) {
            apiHost = apiHost.split("://")[1];
        }
        return DefaultConnectionContext.builder().skipSslValidation(skipSsl)
                .apiHost(apiHost).build();
    }

    @Bean
    @ConditionalOnMissingBean
    ReactorUaaClient uaaClient(ConnectionContext ctx, TokenProvider tokenProvider) {
        return ReactorUaaClient.builder().connectionContext(ctx)
                .tokenProvider(tokenProvider).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordGrantTokenProvider tokenProvider(
            @Value("${cf.user}") String username, @Value("${cf.password}") String password) {
        return PasswordGrantTokenProvider.builder().password(password)
                .username(username).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultCloudFoundryOperations cloudFoundryOperations(
            CloudFoundryClient cloudFoundryClient, ReactorDopplerClient dopplerClient,
            ReactorUaaClient uaaClient, @Value("${cf.org}") String organization,
            @Value("${cf.space}") String space) {
        return DefaultCloudFoundryOperations.builder()
                .cloudFoundryClient(cloudFoundryClient).dopplerClient(dopplerClient)
                .uaaClient(uaaClient).organization(organization).space(space).build();
    }
}