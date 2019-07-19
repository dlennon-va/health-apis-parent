package gov.va.api.health.sentinel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.Method;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Value;

/**
 * This test client supports basic interaction with a service. It assumes that only one content-type
 * is supported, which should be specified.
 */
@Value
@Builder
public final class BasicTestClient implements TestClient {
  private final ServiceDefinition service;

  String contentType;

  /** For post requests, this mapper will be used to convert the object to JSON or XML. */
  Supplier<ObjectMapper> mapper;

  @Override
  public ExpectedResponse get(String path, String... params) {
    return get(null, path, params);
  }

  @Override
  public ExpectedResponse get(Map<String, String> maybeHeaders, String path, String... params) {
    return ExpectedResponse.of(
        service()
            .requestSpecification()
            .contentType(contentType())
            .headers(maybeHeaders == null ? Collections.emptyMap() : maybeHeaders)
            .request()
            .request(Method.GET, path, (Object[]) params));
  }

  @Override
  public ExpectedResponse post(String path, Object body) {
    try {
      return ExpectedResponse.of(
          service()
              .requestSpecification()
              .contentType(contentType())
              .body(mapper.get().writeValueAsString(body))
              .request(Method.POST, path));
    } catch (JsonProcessingException e) {
      throw new AssertionError("Failed to convert body to JSON", e);
    }
  }
}
