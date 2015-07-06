## Phases: Scala macros for multiphase classes

![alt tag](https://raw.githubusercontent.com/jpivarski/phases/master/moon_phases.jpg)

### Motivation

Immutable data is a good idea for many reasons, but it's often compromised in real applications by the need to model processes with staged lifecycles.  For instance, it's common for workflows to have a configuration phase, an initialization phase, a warm-up phase, an execution phase, and a shutdown phase.

These processes can be represented by immutable data, but objects need to be replaced when moving from one phase to the next.  We end up having to write code like

    class DoohickeyInConfiguration(doohickeyParameters: T, configurationParameters: U) {
      ...
    }

    class DoohickeyInInitialization(doohickeyParameters: T, initializationParameters: U) {
      ...
    }

with a lot of boilerplate to copy the unchanging bits of the `Doohickey` from one stage to the next.

HERE







**Warning:** macro updates are not applied unless you `mvn clean`.
