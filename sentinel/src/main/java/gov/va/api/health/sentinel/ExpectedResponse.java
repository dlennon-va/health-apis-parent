package gov.va.api.health.sentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.va.api.health.autoconfig.configuration.JacksonConfig;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * A decorator for the standard Rest Assured response that adds a little more error support, by
 * automatically logging everything if an validation occurs.
 */
@Value
@AllArgsConstructor(staticName = "of")
public class ExpectedResponse {
  Response response;

  /** Expect the HTTP status code to be the given value. */
  public ExpectedResponse expect(int statusCode) {
    try {
      response.then().statusCode(statusCode);
    } catch (AssertionError e) {
      log();
      throw e;
    }
    return this;
  }

  /**
   * Expect the body to be JSON represented by the given type, using the project standard {@link
   * JacksonConfig} object mapper.
   */
  private <T> T expect(Class<T> type) {
    try {
      return JacksonConfig.createMapper().readValue(response().asByteArray(), type);
    } catch (IOException e) {
      log();
      throw new AssertionError("Failed to parse JSON body", e);
    }
  }

  /**
   * Expect the body to be a JSON list represented by the given type, using the project standard
   * {@link JacksonConfig} object mapper.
   */
  public <T> List<T> expectListOf(Class<T> type) {
    try {
      ObjectMapper mapper = JacksonConfig.createMapper();
      return mapper.readValue(
          response().asByteArray(),
          mapper.getTypeFactory().constructCollectionType(List.class, type));
    } catch (IOException e) {
      log();
      throw new AssertionError("Failed to parse JSON body", e);
    }
  }

  /**
   * Expect the body to be JSON represented by the given type, using the project standard {@link
   * JacksonConfig} object mapper, then perform Javax Validation against it.
   */
  public <T> T expectValid(Class<T> type) {
    T payload = expect(type);
    Set<ConstraintViolation<T>> violations =
        Validation.buildDefaultValidatorFactory().getValidator().validate(payload);
    if (violations.isEmpty()) {
      return payload;
    }
    log();
    StringBuilder message = new StringBuilder("Constraint Violations:");
    violations.forEach(
        v ->
            message
                .append('\n')
                .append(v.getMessage())
                .append(": ")
                .append(v.getPropertyPath().toString())
                .append(" = ")
                .append(v.getInvalidValue()));
    message.append("\n\nDetails:");
    violations.forEach(v -> message.append('\n').append(v));
    throw new AssertionError(message.toString());
  }

  @SuppressWarnings("UnusedReturnValue")
  ExpectedResponse log() {
    response().then().log().all();
    return this;
  }
}
