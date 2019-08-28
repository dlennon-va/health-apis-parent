package gov.va.api.health.sentinel.selenium;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Slf4j
public class MyHealtheVetOauthLoginDriver implements OauthLoginDriver {

  @Override
  public void login(WebDriver driver, VaOauthRobot.Configuration.UserCredentials credentials) {
    log.info("Using My HealtheVet");
    driver.findElement(By.className("mhv")).click();
    WebElement user = driver.findElement(By.id("_58_loginField"));
    user.sendKeys(credentials.id());
    WebElement userPassword = driver.findElement(By.id("_58_passwordField"));
    userPassword.sendKeys(credentials.password());
    driver.findElement(By.className("btn-primary")).click();
  }
}
