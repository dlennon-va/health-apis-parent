package gov.va.api.health.autoconfig.configuration;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.ComponentScan;

@Configurable
@ComponentScan(basePackages = "gov.va.api.health.autoconfig.logging")
public class AutoLoggableConfiguration {
  /*
   * Empty to allow for autodiscovery of Loggable components
   */
}
