package gov.va.api.health.sentinel.junit;

import gov.va.api.health.sentinel.LabBot;
import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Junit test rule to perform an Oauth Login using LabBot with application specific parameters and
 * obtain a token result for each user. Token results may be valid or may simply contain an error if
 * there was a problem encountered with the Oauth interface when attempting login/authentication.
 * Results are reported as lists of winners (which appears to mean the user was successfully able to
 * login and obtain a token) and losers (which means the user encountered an error of some kind when
 * attempting login).
 */
@Slf4j
@RequiredArgsConstructor
public class LabBotOauthLoginRule implements TestRule {

  /** Oauth scopes for the application. */
  private final List<String> scopes;

  /** LabBot property file. */
  private final String configFile;

  /** Login request URL. */
  private final String requestUrl;

  /** Expected response json as a string. */
  private final String expectedResponse;

  /** List of winner users (successful login). */
  private final List<String> winners;

  /** List of loser users (unsuccessful login). */
  private final List<String> losers;

  @Override
  public Statement apply(Statement base, Description description) {

    return new Statement() {
      @Override
      public void evaluate() throws Throwable {

        // Report on user results if test is annotated to do so.
        boolean reportFileEnabled =
            description.getAnnotation(LabBotOauthLoginReportFile.class) != null;
        boolean reportEnabled =
            reportFileEnabled || (description.getAnnotation(LabBotOauthLoginReport.class) != null);

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
            winners.add(
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
            losers.add(
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
              Stream.concat(winners.stream().map(w -> w + " - OK"), losers.stream())
                  .sorted()
                  .collect(Collectors.joining("\n"));
          log.info("Lab Users:\n{}", report);
          if (reportFileEnabled) {
            Files.write(
                new File(description.getAnnotation(LabBotOauthLoginReportFile.class).filename())
                    .toPath(),
                report.getBytes(StandardCharsets.UTF_8));
          }
        }

        // Evaluate the unit test.
        base.evaluate();

        // Reset the report.
        winners.clear();
        losers.clear();
      }
    };
  }

  /** Annotation for test class to enable report logging. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface LabBotOauthLoginReport {}

  /** Annotation for test class to enable report file. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface LabBotOauthLoginReportFile {
    /**
     * The filename to write report.
     *
     * @return Filename.
     */
    public String filename() default "users.txt";
  }
}
