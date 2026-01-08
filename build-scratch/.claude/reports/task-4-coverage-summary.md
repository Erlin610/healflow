# Task 4: Tests and Coverage Summary

## Commands (offline / sandbox-friendly)

This workspace runs Maven in-process (`forkCount=0`). To generate fresh JaCoCo data, run tests with the JaCoCo agent attached to the Maven JVM, then generate reports from the produced `target/jacoco.exec`.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$agent = (Resolve-Path .m2/repository/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar)
$env:MAVEN_OPTS="--enable-preview -javaagent:$agent=destfile=target/jacoco.exec,append=false"

mvn --% -o -Dmaven.repo.local=.m2/repository -Denforcer.skip=true -Dmaven.clean.failOnError=false clean test
mvn --% -o -Dmaven.repo.local=.m2/repository -Denforcer.skip=true jacoco:report
mvn --% -o -Dmaven.repo.local=.m2/repository -Denforcer.skip=true jacoco:report-aggregate
```

## Coverage (LINE)

- `healflow-engine/target/site/jacoco/index.html`: `93.03%` (427/459)
- `healflow-platform/target/site/jacoco/index.html`: `98.18%` (54/55)
- `com.healflow.engine.sandbox`: `100.00%` (98/98)
- `com.healflow.platform.service`: `97.22%` (35/36)

## Notes

- `target/site/jacoco-aggregate/index.html` is currently empty; `jacoco:report-aggregate` expects per-module exec data, while this setup emits `target/jacoco.exec` at the multi-module root.
