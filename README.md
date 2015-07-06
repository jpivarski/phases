## Phases: Scala macros for multiphase classes

![alt tag](https://raw.githubusercontent.com/jpivarski/phases/master/moon_phases.jpg)

#### Contents

   * [Motivation](#motivation)
   * [Illustrative example](#illustrative-example)
   * [Realistic use-cases](#realistic-use-cases)
   * [Installation](#installation)

### Motivation

Immutable data is a good idea for many reasons, but it's often compromised in real applications by the need to model processes with staged lifecycles.  For instance, it's common for workflows to have a configuration phase, an initialization phase, a warm-up phase, an execution phase, a shutdown phase, and possibly others.

Staged processes can certainly be represented by immutable data, but objects must be replaced when moving from one phase to the next.  We end up having to write code like

    class ConfigDuringInitialization(parameters: T*) {
      def setParameter(param: String, value: T): ConfigDuringInitialization
      def getParameter(param: String): T
    }

    class ConfigDuringExecution(parameters: T*) {
      // can't change those parameters now!
      def getParameter(param: String): T
    }

with a lot of boilerplate to copy the parts that don't change from one phase's representation to the next.  It's tempting to just write the following with a mutable `var phase`:

    class Config(var phase: P, parameters: T*) {
      def setParameter(param: String, value: T): Config =
        if (phase == Init)
          changeIt()
        else
          throw new UnsupportedOperationForThisPhase()

      def getParameter(param: String): T
    }

After all, it's conceptually one object, so why shouldn't it be represented by one class?  Moreover, some functions that accept a `Config` shouldn't care which phase it's in.  To do things The Right Way, we'd have to set up a class hierarchy or set of interfaces, which is yet more boilerplate.

This library builds hierarchies of classes to represent one conceptual object as it passes through the phases of a state diagram.  You, the user, write one class definition with annotations to indicate which parts of the class are timeless and which are associated with one or several phases.  The macro generates the classes, the transitions, and the type relationships.

The idea for this project was inspired by [Beamer](https://bitbucket.org/rivanvx/beamer/wiki/Home), a LaTeX package for making slide presentations.  It has a [neat syntax](http://www.texdev.net/2014/01/17/the-beamer-slide-overlay-concept/) for creating sequences in which most of the data on two successive slides stays the same, while the important part changes.

### Illustrative example

Suppose we want to model astroliths ("space rocks") which are called "meteorites" while they're in space, "meteors" while they're falling through the atmosphere, and "meteoroids" when they're on the ground.  To do that, we define one class with annotations:

    import io.github.jpivarski.phases

    @phases.declare(meteoroid -> meteor, meteor -> meteorite)
    class Astrolith(composition: String,
                    @meteoroid orbitalVelocity: Double,
                    @meteor impactDate: Long,
                    @meteorite massOfRemnant: Double)

The `@phases.declare` annotation labels the class as one that may undergo phase transitions, and `@meteoroid`, `@meteor`, `@meteorite` are the phases.  There are only two transitions among the three phases: meteorites cannot become meteoroids (not easily, at least).  In general, any [finite state diagram](https://en.wikipedia.org/wiki/State_diagram) could be represented.

During compilation, this class definition is replaced with:

   * class `Astrolith`, which only has a `composition` (all annotated members are removed)
      * static subclass `Astrolith.meteoroid` with constructor signature `(composition: String, orbitalVelocity: Double)` and a `toMeteor` method
      * static subclass `Astrolith.meteor`, with constructor signature `(composition: String, impactDate: Long)` and a `toMeteorite` method
      * static subclass `Astrolith.meteorite`, with constructor signature `(composition: String, massOfRemnant: Double)` and _no_ transition methods.

If desired, the definitions generated classes can be printed out at compile time by adding `(debug = true)` to the end of the annotation:

    @phases.declare(meteoroid -> meteor, meteor -> meteorite)(debug = true)

This can make it easier to understand certain compilation errors downstream.

We've seen that the phases can have different constructors, but they may need different contents and methods, too.  The same annotations apply:

    @phases.declare(meteoroid -> meteor, meteor -> meteorite)
    class Astrolith(composition: String,
                    @meteoroid orbitalVelocity_km_s: Double,
                    @meteor val impactDate_ts: Long,            // works with val
                    @meteorite var massOfRemnant_kg: Double     // works with var
                   ) {
      println("constructing " + this.getClass.getName)

      // defined for all phases
      def composition_symb = composition.replace("nickel", "Ni").replace("iron", "Fe")

      @meteoroid  // defined in only one phase
      def orbitalVelocity_mi_h = 2236.93 * orbitalVelocity_km_s

      @meteor     // defined in only one phase
      def impactDate_year = impactDate_ts / (365L * 24L * 60L * 60L * 1000L) + 1970

      @meteorite  // defined in only one phase
      def massOfRemnant_lb = 2.2046 * massOfRemnant_kg
    }

You can create any phase directly with a `new` operator.  Let's start with the initial state:

    val peekskill_meteoroid = new Astrolith.meteoroid("nickel-iron", 14.0)

and use the transition methods to make a meteor and a meteoroid (in that order):

    // already knows the composition, passes it on through the transition
    val peekskill_meteor = peekskill_meteoroid.toMeteor(718674480000L)  // 1992
    val peekskill_meteorite = peekskill_meteor.toMeteorite(12.4)
    println(peekskill_meteorite.composition_symb)

Functions that require an `Astrolith` in any phase should reference the superclass type:

    def wantsAnyAstrolith(x: Astrolith)

    wantsAnyAstrolith(peekskill_meteoroid)
    wantsAnyAstrolith(peekskill_meteor)
    wantsAnyAstrolith(peekskill_meteorite)

### Realistic use-cases

   * A configuration that is a mutable map before initialization and an immutable map afterward.
   * A wrapper around a `Vector.Builder` in the data-accumulation phase and a `Vector` in the storage and retrieval phase.
   * Avoiding `null` pointers before some fields can be initialized, or equivalently, avoiding the indirection of `Option[T]` forever after initialization.
   * Optimization schemes that convert collections into Scala `Lists` when most operations are head-prepend/retrieval and Scala `Vectors` when most operations are random access.  These transitions could go in both directions.
   * Many more; it seems to come up a lot.

### Installation

Currently, it appears to be necessary to add a

    <compilerPlugins>
      <compilerPlugin>
        <groupId>org.scalamacros</groupId>
        <artifactId>paradise_2.10.5</artifactId>
        <version>2.1.0-M5</version>
      </compilerPlugin>
    </compilerPlugins>

to `scala-maven-plugin` to actually use the macro.  If you think the macro isn't doing anything, turn on `debug = true` to be sure (it should print blocks of code at compile-time).  See the `pom.xml` for this project as a guide.

The macro cannot (currently!) be used in the Scala console, which is a shame, because that's a good place to test things like this.  You have to write your code in a build process that includes the above plugin.

I'm [asking about deployment issues](http://stackoverflow.com/questions/31236360/how-do-i-distribute-a-scala-macro-as-a-project) now to try to streamline this process, so that it can be installed with a simple JAR dependency.  (When available, I'll put revisioned JARs in the appropriate place in GitHub and full instructions here.)

When I have a nice deployment strategy, I'll make both Scala 2.10 and Scala 2.11 versions available (always with the highest minor revision numbers).

Note that you must `mvn clean` between compilations if you make any changes to your annotated classes or the macro itself.  This is not a Zinc issue and is not resolved by changing `<recompileMode>incremental</recompileMode>` to `all`.
