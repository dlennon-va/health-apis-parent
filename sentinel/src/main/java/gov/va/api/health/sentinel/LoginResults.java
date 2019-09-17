package gov.va.api.health.sentinel;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Capture login results as lists of winners (which appears to mean the user was successfully able
 * to login and obtain a token) and losers (which means the user encountered an error of some kind
 * when attempting login).
 */
@Data
public class LoginResults {

  /** List of winner users (successful login). */
  private List<String> winners = new ArrayList<>();

  /** List of loser users (unsuccessful login). */
  private List<String> losers = new ArrayList<>();
}
