## Phases: Scala macros for multiphase classes

![alt tag](https://raw.githubusercontent.com/jpivarski/phases/master/moon_phases.jpg)

### Motivation

Immutable data is a good idea for many reasons, but it's often compromised in real applications by the need to model processes with staged lifecycles.  For instance, it's common for workflows to have a configuration phase, an initialization phase, a warm-up phase, an execution phase, a shutdown phase, and possibly others.

Staged processes can certainly be represented by immutable data, but objects must be replaced when moving from one phase to the next.  We end up having to write code like

    class ConfigDuringInit(parameters: T*) {
      def setParameter(param: String, value: T): ConfigDuringInit
      def getParameter(param: String): T
    }

    class ConfigDuringExec(parameters: T*) {
      // can't change those parameters now!
      def getParameter(param: String): T
    }

with a lot of boilerplate to copy the parts that don't change from one phase's representation to the next.  It's tempting to just write

    class Config(var phase: P, parameters: T*) {
      def setParameter(param: String, value: T): ConfigDuringInit =
        if (phase == Init)
          changeIt()
        else
          throw new UnsupportedOperationForThisPhase()

      def getParameter(param: String): T
    }

After all, it's conceptually one object, why shouldn't it be represented by one class?  Moreover, some functions that accepta a `Config` shouldn't care which phase it's in.  To do it right, we'd have to set up a class hierarchy or set of interfaces, which is yet more boilerplate.

This library builds hierarchies of classes to represent one object as it passes through the phases of a state diagram.  You, the user, write one class definition with annotations to indicate which parts of the class are timeless and which are associated with one or several phases.  The macro generates the classes, the transitions, and the type relationships.

The idea for this project was inspired by [Beamer](https://bitbucket.org/rivanvx/beamer/wiki/Home), a LaTeX package for making slide presentations.  It has a [neat syntax](http://www.texdev.net/2014/01/17/the-beamer-slide-overlay-concept/) for creating sequences in which most data on the slide stays the same while one piece gets updated.


HERE







**Warning:** macro updates are not applied unless you `mvn clean`.
