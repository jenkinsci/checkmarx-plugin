# Checkmarx SAST Plugin for Jenkins

[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/checkmarx-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/checkmarx-plugin/)

For information about this plugin check its [Wiki](https://wiki.jenkins-ci.org/display/JENKINS/Checkmarx+CxSAST+Plugin).

---

## Requirements

| Requirement | Minimum Version |
|-------------|----------------|
| Java | 21 |
| Jenkins | 2.541.1 |
| Gradle (build only) | 8.14 |

> **Note:** Java 17 support in Jenkins ends on or after March 31, 2026. This plugin requires Java 21 or higher.

---

## Java Version Compatibility

| Java Version| Build         | Runtime (Jenkins) |
|-------------|---------      |-------------------|
| Java 8/11/17| Not supported | Not supported |
| Java 21     | Supported     | Supported |
| Java 25     | Not supported | Supported |

---

## Building from Source

Ensure `JAVA_HOME` is set to a JDK 21 before building.

```bash
./gradlew clean build jpi
```

The built plugin will be available at:

```
build/libs/checkmarx.hpi
```

---

## Running Tests

```bash
./gradlew test
```

---

## Installation

1. Go to **Jenkins > Manage Jenkins > Plugins > Advanced**
2. Under **Deploy Plugin**, upload the `checkmarx.hpi` file
3. Restart Jenkins

---


