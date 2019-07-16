### init-script

Requirements:
* A linux machine (script uses linux specific directories and file types)

Functionality:
* Install Java 8 or 12
* Install Maven 3.5.4
* Create/Update `JAVA_HOME`, `M2_HOME`, `MAVEN_HOME`, `PATH`

Usage:
* `init-script` : installs jdk12 and maven on linux machine
* `init-script 8` : installs jdk8 and maven on linux machine

---

### trust-dev-certs.sh

Requirements:
* Root access to modify the trust store
* The following will need to be set: `JAVA_HOME`, `HEALTH_API_CERTIFICATE_PASSWORD`

Functionality:
* Updates cacerts trust store in Java home to trust:
  * The nexus.freedomstream.io certificate.
  * The tools.health.dev-developer.va.gov certificate.

Usage:
* `trust-dev-certs.sh` : Updates cacerts
