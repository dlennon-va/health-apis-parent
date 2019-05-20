package gov.va.api.health.sentinel;

import io.restassured.response.ResponseBody;

@FunctionalInterface
public interface ErrorsAreFunctionallyEqual {
  boolean equals(ResponseBody<?> responseBody1, ResponseBody<?> responseBody2);
}
