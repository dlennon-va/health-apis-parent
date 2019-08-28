package gov.va.api.health.sentinel.selenium;

import org.openqa.selenium.WebDriver;

public interface OauthLoginDriver {

  void login(WebDriver driver, VaOauthRobot.Configuration.UserCredentials credentials);
}
