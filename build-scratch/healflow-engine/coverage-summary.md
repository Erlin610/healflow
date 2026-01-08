# Task-1 Coverage Summary (healflow-engine)

Generated JaCoCo report: `healflow-engine/target/site/jacoco/index.html`

## Test selection

Maven test filter:

`-Dtest=*Config*Test,*Sandbox*Test`

## LINE coverage

Overall LINE coverage: **91.76%** (390/425)

Per-package LINE coverage:

- `com/healflow/engine`: 100% (15/15)
- `com/healflow/engine/config`: 100% (3/3)
- `com/healflow/engine/git`: 88.64% (117/132)
- `com/healflow/engine/sandbox`: 96.88% (62/64)
- `com/healflow/engine/shell`: 90.78% (128/141)
- `com/healflow/engine/utils`: 100% (12/12)
- `com/healflow/engine/workspace`: 91.38% (53/58)

## Notes

In this sandbox, JaCoCo execution data is written to `target/jacoco.exec` when the Maven JVM exits, so `jacoco:report` may need to be run once more after tests to pick up the dumped data:

`mvn -pl healflow-engine jacoco:report`

