package loggregator;

import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.LogsRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class LoggregatorApplication {

    public static void main(String args[]) throws Throwable {
        SpringApplication.run(LoggregatorApplication.class, args);
    }
}

@Component
@Slf4j
class LoggregatorClient {

    private final CloudFoundryOperations cf;
    private String applicationName = "hipy";

    LoggregatorClient(CloudFoundryOperations cf) {
        this.cf = cf;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready(ApplicationReadyEvent evt) {
        cf.applications()
                .logs(LogsRequest.builder().name(this.applicationName).build())
                .subscribe(lm -> LoggregatorClient.log.info(lm.getApplicationId() + ':' + lm.getMessage()));
    }
}