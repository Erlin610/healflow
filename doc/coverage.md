# Coverage

## Prerequisites

- JDK 21+
- Maven 3.9+

## Build

- PowerShell: `mvn clean verify "-Denforcer.skip=false"`
- Bash/Zsh: `mvn clean verify -Denforcer.skip=false`

## Reports

- Per-module HTML: `healflow-*/target/site/jacoco/index.html`
- Aggregate HTML: `target/site/jacoco-aggregate/index.html`
- Coverage summary (text): `target/coverage-summary.txt` (also echoed during `verify`)

## Threshold

`jacoco:check` enforces `LINE` covered ratio `>= 0.90` per module.

