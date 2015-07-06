## Phases: Scala macros for multiphase classes

![alt tag](https://raw.githubusercontent.com/jpivarski/phases/master/moon_phases.jpg)

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

with a lot of boilerplate to copy the parts that don't change from one phase's representation to the next.  It's tempting to just write

    class Config(var phase: P, parameters: T*) {
      def setParameter(param: String, value: T): Config =
        if (phase == Init)
          changeIt()
        else
          throw new UnsupportedOperationForThisPhase()

      def getParameter(param: String): T
    }

After all, it's conceptually one object, so why shouldn't it be represented by one class?  Moreover, some functions that accept a `Config` shouldn't care which phase it's in.  To do things the Right Way, we'd have to set up a class hierarchy or set of interfaces, which is yet more boilerplate.

This library builds hierarchies of classes to represent one object as it passes through the phases of a state diagram.  You, the user, write one class definition with annotations to indicate which parts of the class are timeless and which are associated with one or several phases.  The macro generates the classes, the transitions, and the type relationships.

The idea for this project was inspired by [Beamer](https://bitbucket.org/rivanvx/beamer/wiki/Home), a LaTeX package for making slide presentations.  It has a [neat syntax](http://www.texdev.net/2014/01/17/the-beamer-slide-overlay-concept/) for creating sequences in which most of the data on two successive slides stays the same, while the important part changes.

### Illustrative example

Suppose we want to model astroliths ("space rocks") which are called "meteorites" while they're in space, "meteors" while they're falling through the atmosphere, and "meteoroids" when they're on the ground.  To do that, we define one class with annotations:

    import io.github.jpivarski.phases

    @phases.declare(meteoroid -> meteor, meteor -> meteorite)
    class Astrolith(composition: String,
                    @meteoroid orbitalVelocity: Double,
                    @meteor impactDate: Long,
                    @meteorite massOfRemnant: Double)

The `@phases.declare` annotation labels the class as one that may undergo phase transitions, and the `@meteoroid`, `@meteor`, @meteorite` are the phases.  There are only two transitions among the three phases: meteorites cannot become meteoroids (not easily, at least).  In general, any finite state diagram could be represented.

During compilation, this class definition is replaced with:

   * superclass `Astrolith`, which only has a `composition`
      * subclass `Astrolith.meteoroid`, which has a `composition`, an `orbitalVelocity`, and a `toMeteor` function
      * subclass `Astrolith.meteor`, which has a `composition`, an `impactDate`, and a `toMeteorite` function
      * subclass `Astrolith.meteorite`, which has a `composition` and a `massOfRemnant`

If desired, the generated classes can be printed out at compile time by adding

    @phases.declare(meteoroid -> meteor, meteor -> meteorite)(debug = true)

to the annotation.

Since the different phases have different constructors, they may need different contents and methods, too.  This example comes from the unit tests:

    @phases.declare(meteoroid -> meteor, meteor -> meteorite)
    class Astrolith(composition: String,
                    @meteoroid orbitalVelocity_km_s: Double,
                    @meteor val impactDate_ts: Long,            // works with val
                    @meteorite var massOfRemnant_kg: Double     // works with var
                   ) {
      println("constructing " + this.getClass.getName)  // only once per object

      // defined for all phases
      def composition_symb = composition.replace("nickel", "Ni").replace("iron", "Fe")

      @meteoroid  // only one phase
      def orbitalVelocity_mi_h = 2236.93 * orbitalVelocity_km_s

      @meteor     // only one phase
      def impactDate_year = impactDate_ts / (365L * 24L * 60L * 60L * 1000L) + 1970

      @meteorite  // only one phase
      def massOfRemnant_lb = 2.2046 * massOfRemnant_kg
    }

You can create any state directly, but let's start with the initial state:

    val peekskill_meteoroid = new Astrolith.meteoroid("nickel-iron", 14.0)

and use the transition function to make a meteor and a meteoroid:

    // already knows the composition, passes this on through the transition
    val peekskill_meteor = peekskill_meteoroid.toMeteor(718674480000L)
    val peekskill_meteorite = peekskill_meteor.toMeteorite(12.4)

Functions that require an `Astrolith` in any phase should reference the superclass type:

    def wantsAnyAstrolith(x: Astrolith)

    wantsAnyAstrolith(peekskill_meteoroid)
    wantsAnyAstrolith(peekskill_meteor)
    wantsAnyAstrolith(peekskill_meteorite)

### Realistic use-cases







### HERE

**Warning:** macro updates are not applied unless you `mvn clean`.
