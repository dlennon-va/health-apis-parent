package gov.va.api.health.sentinel.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
