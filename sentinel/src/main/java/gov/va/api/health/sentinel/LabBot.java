package gov.va.api.health.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import gov.va.api.health.sentinel.LabBot.LabBotUserResult.LabBotUserResultBuilder;
import gov.va.api.health.sentinel.selenium.IdMeOauthRobot;
import gov.va.api.health.sentinel.selenium.IdMeOauthRobot.Configuration;
import gov.va.api.health.sentinel.selenium.IdMeOauthRobot.Configuration.Authorization;
import gov.va.api.health.sentinel.selenium.IdMeOauthRobot.Configuration.Authorization.AuthorizationBuilder;
import gov.va.api.health.sentinel.selenium.IdMeOauthRobot.Configuration.UserCredentials;
import gov.va.api.health.sentinel.selenium.IdMeOauthRobot.OAuthCredentialsMode;
import gov.va.api.health.sentinel.selenium.IdMeOauthRobot.TokenExchange;
import io.restassured.RestAssured;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

@Slf4j
@Value
public class LabBot {

  @NonNull List<String> scopes;

  @Getter(AccessLevel.PRIVATE)
  Config config;

  @NonNull List<String> userIds;

  /**
   * Builds what is required by LabBot.
   *
   * @param scopes scopes that LabBot will for token requests
   * @param userIds userIds that LabBot will get tokens and make requests against
   * @param configFile configFile that LabBot grabs its configuration properties from
   */
  @Builder
  public LabBot(
      @NonNull List<String> scopes, @NonNull List<String> userIds, @NonNull String configFile) {
    this.scopes = scopes;
    this.userIds = userIds;
    config = new LabBot.Config(configFile);
  }

  /** Gets all Lab users. */
  public static List<String> allUsers() {
    List<String> allUsers = new LinkedList<>();
    for (int i = 1; i <= 5; i++) {
      allUsers.add("vasdvp+IDME_" + String.format("%02d", i) + "@gmail.com");
    }
    for (int i = 101; i < 183; i++) {
      allUsers.add("va.api.user+idme." + String.format("%03d", i) + "@gmail.com");
    }
    return allUsers;
  }

  private Authorization makeAuthorization(SmartOnFhirUrls urls) {
    AuthorizationBuilder authorizationBuilder = Authorization.builder();
    authorizationBuilder
        .clientId(config.clientId())
        .clientSecret(config.clientSecret())
        .authorizeUrl(urls.authorize())
        .redirectUrl(config.redirectUrl())
        .state(config.state())
        .aud(config.aud());
    for (String scope : scopes) {
      authorizationBuilder.scope(scope);
    }
    return authorizationBuilder.build();
  }

  /**
   * Creates IdMeOauthRobot with specified user credentials and urls for Lab environment using
   * Chrome Driver.
   *
   * @param userCredentials Credentials for the user to perform operations against.
   * @param baseUrl URLs to perform operations against.
   */
  private IdMeOauthRobot makeLabBot(
      UserCredentials userCredentials, String baseUrl, OAuthCredentialsMode credentialsMode) {
    SmartOnFhirUrls urls = new SmartOnFhirUrls(baseUrl);
    Authorization authorization = makeAuthorization(urls);
    Configuration configuration =
        Configuration.builder()
            .authorization(authorization)
            .tokenUrl(urls.token())
            .user(userCredentials)
            .chromeDriver(config.driver())
            .headless(config.headless())
            .credentialsMode(credentialsMode)
            .build();
    return IdMeOauthRobot.of(configuration);
  }

  /**
   * Given a path send a request for each user. Replaces {icn} with patient icn to send request.
   * Useful for when you want to send a given request to a set of users.
   *
   * @param path The path to use for the requests, must contain {icn} to be replaced with the
   *     patient icn.
   */
  @SneakyThrows
  public List<LabBotUserResult> request(String path) {
    List<LabBotUserResult> tokenUserResultList = tokens();
    List<LabBotUserResult> responseUserResultList = new CopyOnWriteArrayList<>();
    ExecutorService ex = Executors.newFixedThreadPool(10);
    List<Future<?>> futures = new ArrayList<>(userIds.size());
    for (LabBotUserResult tokenResult : tokenUserResultList) {
      futures.add(
          ex.submit(
              () -> {
                LabBotUserResultBuilder resultBuilder =
                    LabBotUserResult.builder()
                        .user(tokenResult.user())
                        .tokenExchange(tokenResult.tokenExchange());
                try {
                  URL baseUrl = new URL(config.baseUrl());
                  URL url =
                      new URL(
                          baseUrl.getProtocol(),
                          baseUrl.getHost(),
                          path.replace("{icn}", tokenResult.tokenExchange.patient()));
                  HttpURLConnection con = (HttpURLConnection) url.openConnection();
                  con.setRequestProperty(
                      "Authorization", "Bearer " + tokenResult.tokenExchange.accessToken());
                  con.setRequestMethod("GET");
                  log.info("Sending request to: " + url.toString());

                  try (var reader =
                      new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)) {
                    resultBuilder.response(CharStreams.toString(reader));
                  }
                } catch (Exception e) {
                  log.error(
                      "Request failure {} {}: {}",
                      tokenResult.user(),
                      path,
                      e.getMessage(),
                      e.getCause());
                  resultBuilder.response(
                      "ERROR: " + e.getClass().getName() + ": " + e.getMessage());
                } finally {
                  responseUserResultList.add(resultBuilder.build());
                }
              }));
    }
    results(ex, futures);
    return responseUserResultList;
  }

  private void results(ExecutorService ex, List<Future<?>> futures) throws InterruptedException {
    ex.shutdown();
    ex.awaitTermination(10, TimeUnit.MINUTES);
    futures.forEach(
        f -> {
          try {
            f.get();
          } catch (Exception e) {
            log.error(e.getMessage());
          }
        });
  }

  /**
   * Returns tokens for each user. Alternates between sending {client-id}:{client-secret} as a
   * header, or in the request body.
   */
  @SneakyThrows
  public List<LabBotUserResult> tokens() {
    /*
     * Make necessary configuration is available.
     */
    config().baseUrl();
    config().userPassword();
    config().aud();
    config().redirectUrl();
    List<LabBotUserResult> labBotUserResultList = new CopyOnWriteArrayList<>();
    ExecutorService ex = Executors.newFixedThreadPool(10);
    List<Future<?>> futures = new ArrayList<>(userIds.size());
    for (String userId : userIds) {
      futures.add(
          ex.submit(
              () -> {
                UserCredentials userCredentials =
                    UserCredentials.builder().id(userId).password(config.userPassword()).build();
                IdMeOauthRobot bot =
                    makeLabBot(userCredentials, config.baseUrl(), config().credentialsMode());
                labBotUserResultList.add(
                    LabBotUserResult.builder()
                        .user(userCredentials)
                        .tokenExchange(bot.token())
                        .build());
              }));
    }
    results(ex, futures);
    return labBotUserResultList;
  }

  private static class Config {

    private Properties properties;

    @SneakyThrows
    Config(String pathname) {
      File file = new File(pathname);
      if (file.exists()) {
        log.info("Loading properties from: {}", file);
        properties = new Properties(System.getProperties());
        try (FileInputStream inputStream = new FileInputStream(file)) {
          properties.load(inputStream);
        }
      } else {
        log.info("Properties not found: {}, using System properties", file);
        properties = System.getProperties();
      }
    }

    String aud() {
      return valueOf("lab.aud");
    }

    String baseUrl() {
      return valueOf("lab.base-url");
    }

    String clientId() {
      return valueOf("lab.client-id");
    }

    String clientSecret() {
      return valueOf("lab.client-secret");
    }

    OAuthCredentialsMode credentialsMode() {
      return OAuthCredentialsMode.valueOf(valueOf("lab.credentials-mode"));
    }

    String driver() {
      return valueOf("webdriver.chrome.driver");
    }

    boolean headless() {
      return BooleanUtils.toBoolean(valueOf("webdriver.chrome.headless"));
    }

    String redirectUrl() {
      return valueOf("lab.redirect-url");
    }

    String state() {
      return valueOf("lab.state");
    }

    String userPassword() {
      return valueOf("lab.user-password");
    }

    private String valueOf(String name) {
      String value = properties.getProperty(name, "");
      assertThat(value).withFailMessage("System property %s must be specified.", name).isNotBlank();
      return value;
    }
  }

  public static class InvalidConformanceStatement extends RuntimeException {
    InvalidConformanceStatement(String message) {
      super(message);
    }
  }

  @Builder
  @Value
  public static class LabBotUserResult {

    UserCredentials user;

    TokenExchange tokenExchange;

    String response;
  }

  @Value
  private static class SmartOnFhirUrls {

    String token;

    String authorize;

    /**
     * Create a new instance that will reach out to the given base URL to discover SMART on FHIR
     * information. This class will attempt to interact with /metadata endpoint of the base URL
     * immediately during construction.
     */
    @SneakyThrows
    private SmartOnFhirUrls(String baseUrl) {
      log.info("Discovering authorization endpoints from {}", baseUrl);
      String statement =
          RestAssured.given().relaxedHTTPSValidation().baseUri(baseUrl).get("metadata").asString();
      ObjectMapper mapper = new ObjectMapper();
      JsonNode node = mapper.readTree(statement);
      JsonNode oauthExtensionNode = findOauthExtensionNode(node);
      token = findTokenUri(oauthExtensionNode);
      authorize = findAuthorizeUri(oauthExtensionNode);
    }

    private static void checkConformanceStatement(boolean ok, String message) {
      if (!ok) {
        throw new InvalidConformanceStatement(message);
      }
    }

    private static <T> Optional<T> checkConformanceStatement(
        Optional<T> notNullObject, String message) {
      checkConformanceStatement(notNullObject != null, message);
      checkConformanceStatement(notNullObject.isPresent(), message);
      return notNullObject;
    }

    private static <T> T checkConformanceStatement(T notNullObject, String message) {
      checkConformanceStatement(notNullObject != null, message);
      return notNullObject;
    }

    private static String findAuthorizeUri(JsonNode jsonNode) {
      Optional<JsonNode> urlAuthorizeNode =
          checkConformanceStatement(
              getParentWith(jsonNode, "url", "authorize"),
              "Unable to find JSON node with 'url:authorize' key value pair");
      JsonNode authorizeNode =
          checkConformanceStatement(
              urlAuthorizeNode.get().path("valueUri"),
              "Unable to find JSON node with 'valueUri' path for authorize");
      return authorizeNode.textValue();
    }

    private static JsonNode findOauthExtensionNode(@NonNull JsonNode jsonNode) {
      JsonNode restNode =
          checkConformanceStatement(
              jsonNode.path("rest"), "Unable to find JSON node with 'rest' path");
      Optional<JsonNode> modeServerNode =
          checkConformanceStatement(
              getParentWith(restNode, "mode", "server"),
              "Unable to find child JSON node with 'mode:server' key:value pair");
      JsonNode securityNode =
          checkConformanceStatement(
              modeServerNode.get().path("security"),
              "Unable to find JSON node with 'security' path");
      JsonNode extensionNode =
          checkConformanceStatement(
              securityNode.path("extension"),
              "Unable to find JSON node with 'extension' path for securityNode");
      Optional<JsonNode> oauthUriNode =
          checkConformanceStatement(
              getParentWith(
                  extensionNode,
                  "url",
                  "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris"),
              "Unable to find JSON node with 'url:http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris' key value pair");
      return checkConformanceStatement(
          oauthUriNode.get().path("extension"),
          "Unable to find JSON node with 'extension' path for oauthUriNode");
    }

    private static String findTokenUri(JsonNode oauthUriNode) {
      Optional<JsonNode> urlTokenNode =
          checkConformanceStatement(
              getParentWith(oauthUriNode, "url", "token"),
              "Unable to find JSON node with 'url:token' key value pair");
      JsonNode tokenNode =
          checkConformanceStatement(
              urlTokenNode.get().path("valueUri"),
              "Unable to find JSON node with 'valueUri' path for token");
      return tokenNode.textValue();
    }

    /** Get json parent node with given key value pair. * */
    private static Optional<JsonNode> getParentWith(
        JsonNode node, @NonNull String key, @NonNull String value) {
      for (JsonNode checkNode : node) {
        if ((value).equals(checkNode.path(key).textValue())) {
          return Optional.of(checkNode);
        }
      }
      return Optional.empty();
    }
  }
}
