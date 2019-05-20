package gov.va.api.health.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.Method;
import io.restassured.response.Response;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * The FhirTestClient bakes in some functionality such as making the requests with all three
 * supported content types: application/json, application/fhir+json, and application/json+fhir. When
 * using this test client, callers will need to furnish an ErrorsAreFunctionallyEqual that will
 * determine whether each of the three content types returns a functionally equivalent (ignoring
 * transient data like timestamps and sequence numbers) response.
 */
@Slf4j
@Value
@Builder
public final class FhirTestClient implements TestClient {
  ServiceDefinition service;

  ExecutorService executorService =
      Executors.newFixedThreadPool(
          SentinelProperties.threadCount(
              "sentinel.threads", Runtime.getRuntime().availableProcessors()));

  @NonNull ErrorsAreFunctionallyEqual errorResponseEqualityCheck;

  Supplier<ObjectMapper> mapper;

  @Override
  @SneakyThrows
  public ExpectedResponse get(String path, String... params) {
    Future<Response> baselineResponseFuture =
        executorService.submit(
            () -> {
              return get("application/json", path, params);
            });

    if (path.startsWith("/actuator")) {
      /* Health checks, metrics, etc. do not have FHIR compliance requirements */
      return ExpectedResponse.of(baselineResponseFuture.get(5, TimeUnit.MINUTES));
    }

    Future<Response> fhirJsonResponseFuture =
        executorService.submit(
            () -> {
              return get("application/fhir+json", path, params);
            });
    Future<Response> jsonFhirResponseFuture =
        executorService.submit(
            () -> {
              return get("application/json+fhir", path, params);
            });

    final Response baselineResponse = baselineResponseFuture.get(5, TimeUnit.MINUTES);
    final Response fhirJsonResponse = fhirJsonResponseFuture.get(5, TimeUnit.MINUTES);
    final Response jsonFhirResponse = jsonFhirResponseFuture.get(5, TimeUnit.MINUTES);

    assertThat(fhirJsonResponse.getStatusCode())
        .withFailMessage(
            "status: application/json ("
                + baselineResponse.getStatusCode()
                + ") does not equal application/fhir+json ("
                + fhirJsonResponse.getStatusCode()
                + ")")
        .isEqualTo(baselineResponse.getStatusCode());
    assertThat(jsonFhirResponse.getStatusCode())
        .withFailMessage(
            "status: application/json ("
                + baselineResponse.getStatusCode()
                + ") does not equal application/json+fhir ("
                + jsonFhirResponse.getStatusCode()
                + ")")
        .isEqualTo(baselineResponse.getStatusCode());

    if (baselineResponse.getStatusCode() >= 400) {
      /*
       * Error responses must be returned as OOs but contain a timestamp in the diagnostics
       * that prevents direct comparison.
       */
      assertThat(
              errorResponseEqualityCheck.equals(baselineResponse.body(), fhirJsonResponse.body()))
          .isTrue();
      assertThat(
              errorResponseEqualityCheck.equals(baselineResponse.body(), jsonFhirResponse.body()))
          .isTrue();
    } else {
      // OK responses
      assertThat(baselineResponse.body().asString())
          .isEqualTo(fhirJsonResponse.body().asString())
          .isEqualTo(jsonFhirResponse.body().asString());
    }
    return ExpectedResponse.of(baselineResponse);
  }

  private Response get(String contentType, String path, Object[] params) {
    Response response = null;

    // We'll make the request at least one time and as many as maxAttempts if we get a 500 error.
    final int maxAttempts = 3;
    for (int i = 0; i < maxAttempts; i++) {
      if (i > 0) {
        log.info("Making retry attempt {} for {}:{} after failure.", i, contentType, path);
      }

      response =
          service()
              .requestSpecification()
              .contentType(contentType)
              .accept(contentType)
              .request(Method.GET, path, params);

      if (response.getStatusCode() != 500) {
        return response;
      }
    }

    return response;
  }

  @Override
  public ExpectedResponse post(String path, Object body) {
    return ExpectedResponse.of(
        service()
            .requestSpecification()
            .contentType("application/fhir+json")
            .accept("application/fhir+json")
            .body(body)
            .request(Method.POST, path));
  }
}
