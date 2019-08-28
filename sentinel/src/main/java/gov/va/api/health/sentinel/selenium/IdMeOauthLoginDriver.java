package gov.va.api.health.sentinel.selenium;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Slf4j
public class IdMeOauthLoginDriver implements OauthLoginDriver {

  @Override
  public void login(WebDriver driver, VaOauthRobot.Configuration.UserCredentials credentials) {
    log.info("Using ID.me");
    driver.findElement(By.className("idme-signin")).click();
    WebElement user = driver.findElement(By.id("user_email"));
    user.sendKeys(credentials.id());
    WebElement userPassword = driver.findElement(By.id("user_password"));
    userPassword.sendKeys(credentials.password());
    driver.findElement(By.name("commit")).click();
  }
}
