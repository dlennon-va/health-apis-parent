# sentinel

Sentinel is a library to support acceptance testing to verify the correctness of a system.

##### Key Concepts
- A `Service Definition` defines the particulars for interacting with a given service, such as base
  URL and port.
- A supported `Environment` is one of _Lab_, _Local_, _Production_, _QA_, or _Staging_.
     - Use the system property `sentinel` to set the active environment, e.g. `-Dsentinel=LAB`
- A `Test Client` is a Java interface that specializes with REST interaction for a specific `Service Definition`.
      - One simple implementation, `BasicTestClient` is provided.

##### Categories
JUnit categories are used to control which integration tests are executed.
- Categories are additive. Each integration test method should be annotated with all of the categories in which it should execute.
- Clients should provide their own implementations, based on their environments and configurations.
     - Categories must be defined in the `gov.va.api.health.sentinel.categories` package.
- Sentinel provides two basic categories:
     - `Local` category denotes integration tests that execute during local builds.
     - `Manual` category denotes integration tests that should only be executed on a manual, ad-hoc basis.

##### Selenium
Sentinel provides several Selenium-based tools.
- `DevApiPortal` provides access to the developer API portal page.
- `VaOauthRobot` is a robot user that can click through all of the authentication prompts.
