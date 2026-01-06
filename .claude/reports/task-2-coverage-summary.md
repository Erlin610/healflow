# Task 2: InteractiveRunner - Coverage Summary

## Commands (offline / sandbox-friendly)

```powershell
Set-Location E:\mine\healflow

$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$agent = (Resolve-Path .m2/repository/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar)

Remove-Item -Force -ErrorAction SilentlyContinue target/jacoco.exec
$env:MAVEN_OPTS="--enable-preview -javaagent:$agent=destfile=target/jacoco.exec,append=false"
cmd /c "mvn -o -Dmaven.repo.local=.m2/repository -Denforcer.skip=true -Dmaven.clean.failOnError=false clean test -pl healflow-engine -Dtest=InteractiveRunnerTest"
Remove-Item Env:\MAVEN_OPTS
cmd /c "mvn -o -Dmaven.repo.local=.m2/repository -Denforcer.skip=true -pl healflow-engine jacoco:report"
```

## Coverage (JaCoCo, `LINE`)

- `healflow-engine/target/site/jacoco/com.healflow.engine.sandbox/InteractiveRunner.java.html`: `100.00%` (43/43)

