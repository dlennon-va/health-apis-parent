package gov.va.api.health.autoconfig.configuration.testapp;

import gov.va.api.health.autoconfig.logging.Loggable;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
@Loggable
public class FugaziComponent {
  public Instant now() {
    return Instant.now();
  }
}
