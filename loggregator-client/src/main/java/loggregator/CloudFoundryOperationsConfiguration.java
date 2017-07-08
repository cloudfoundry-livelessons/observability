package loggregator;


import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.operations.applications.LogsRequest;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.cloudfoundry.reactor.uaa.ReactorUaaClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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