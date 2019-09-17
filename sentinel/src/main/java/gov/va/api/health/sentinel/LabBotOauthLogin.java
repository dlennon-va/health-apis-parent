package gov.va.api.health.sentinel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Perform an Oauth Login using LabBot with application specific parameters and obtain a token
 * result for each user. Token results may be valid or may simply contain an error if there was a
 * problem encountered with the Oauth interface when attempting login/authentication. Results are
 * reported as lists of winners (which appears to mean the user was successfully able to login and
 * obtain a token) and losers (which means the user encountered an error of some kind when
 * attempting login).
 */
@Slf4j
@Builder
public class LabBotOauthLogin {

  /** Oauth scopes for the application. */
  private List<String> scopes;

  /** LabBot property file. */
  private String configFile;

  /** Login request URL. */
  private String requestUrl;

  /** Expected response json as a string. */
  private String expectedResponse;

  /** Optionally enable report logging. */
  private boolean reportEnabled;

  /** Optionally enable report file. Applies only if report is enabled. */
  private boolean reportFileEnabled;

  /** Report file. */
  @Builder.Default private File reportFile = new File("users.txt");

  /**
   * Login users capturing results and optionally log and create report file of report.
   *
   * @return Bean containing list of login results.
   * @throws IOException Exception if report file could not be created.
   */
  public LoginResults loginUsers() throws IOException {

    LoginResults loginResults = new LoginResults();

    // Use the LabBot with the provided configuration to attempt login request for all LabBot
    // users.
    List<LabBot.LabBotUserResult> labBotUserResultList =
        LabBot.builder()
            .userIds(LabBot.allUsers())
            .scopes(scopes)
            .configFile(configFile)
            .build()
            .request(requestUrl);

    // Sort successful and unsuccessful users into appropriate lists.
    for (LabBot.LabBotUserResult labBotUserResult : labBotUserResultList) {
      if (!labBotUserResult.tokenExchange().isError()
          && labBotUserResult.response().contains(expectedResponse)) {
        if (reportEnabled) {
          log.info(
              "Winner: {} is patient {}.",
              labBotUserResult.user().id(),
              labBotUserResult.tokenExchange().patient());
        }
        loginResults
            .winners()
            .add(
                labBotUserResult.user().id()
                    + " is patient "
                    + labBotUserResult.tokenExchange().patient());
      } else {
        if (reportEnabled) {
          log.info(
              "Loser: {} is patient {}.",
              labBotUserResult.user().id(),
              labBotUserResult.tokenExchange().patient());
        }
        loginResults
            .losers()
            .add(
                labBotUserResult.user().id()
                    + " is patient "
                    + labBotUserResult.tokenExchange().patient()
                    + " - "
                    + labBotUserResult.tokenExchange().error()
                    + ": "
                    + labBotUserResult.tokenExchange().errorDescription());
      }
    }

    if (reportEnabled) {
      String report =
          Stream.concat(
                  loginResults.winners().stream().map(w -> w + " - OK"),
                  loginResults.losers().stream())
              .sorted()
              .collect(Collectors.joining("\n"));
      log.info("Lab Users:\n{}", report);
      if (reportFileEnabled) {
        Files.write(reportFile.toPath(), report.getBytes(StandardCharsets.UTF_8));
      }
    }

    return loginResults;
  }
}
