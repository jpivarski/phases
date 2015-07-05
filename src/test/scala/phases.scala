package test.scala.phases

import org.junit.runner.RunWith

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers

import io.github.jpivarski.phases
import test.scala._

@RunWith(classOf[JUnitRunner])
class PhasesSuite extends FlatSpec with Matchers {

  // an example: Meteoroids (rocks in space), Meteors (falling rocks), and Meteorite (rocks on the ground)
  // are all rocks from space ("Astroliths?")
  // Meteoroids can become Meteors and Meteors can become Meteorites, but those are the only allowed transitions

  @phases.declare(Meteoroid -> Meteor, Meteor -> Meteorite)
  class Astrolith(composition: String, @Meteoroid orbitalVelocity_km_s: Double, @Meteor val impactDate_ts: Long, @Meteorite var massOfRemnant_kg: Double) {
    println("constructing " + this.getClass.getName)  // should only happen once per object: see print-out

    // defined for all phases
    def composition_symb = composition.replace("nickel", "Ni").replace("iron", "Fe")

    @Meteoroid
    def orbitalVelocity_mi_h = 2236.93 * orbitalVelocity_km_s

    @Meteor
    def impactDate_year = impactDate_ts / (365L * 24L * 60L * 60L * 1000L) + 1970

    @Meteorite
    def massOfRemnant_lb = 2.2046 * massOfRemnant_kg
  }

  // for verifying that the phased objects have the right polymorphism
  def wantsAstrolith(x: Astrolith) = x
  def wantsMeteoroid(x: Astrolith.Meteoroid) = x
  def wantsMeteor(x: Astrolith.Meteor) = x
  def wantsMeteorite(x: Astrolith.Meteorite) = x

  // first time around, don't use the transitions: just construct everything directly
  "astrolith example" must "work for direct constructors" in {
    val peekskill = new Astrolith("nickel-iron")
    peekskill.composition_symb should be ("Ni-Fe")
    wantsAstrolith(peekskill)

    val peekskill_meteoroid = new Astrolith.Meteoroid("nickel-iron", 14.0)
    peekskill_meteoroid.composition_symb should be ("Ni-Fe")
    peekskill_meteoroid.orbitalVelocity_mi_h should be (31317.02 +- 0.01)
    wantsAstrolith(peekskill_meteoroid)
    wantsMeteoroid(peekskill_meteoroid)

    val peekskill_meteor = new Astrolith.Meteor("nickel-iron", 718674480000L)
    peekskill_meteor.composition_symb should be ("Ni-Fe")
    peekskill_meteor.impactDate_year should be (1992)
    peekskill_meteor.impactDate_ts should be (718674480000L)
    wantsAstrolith(peekskill_meteor)
    wantsMeteor(peekskill_meteor)

    val peekskill_meteorite = new Astrolith.Meteorite("nickel-iron", 12.4)
    peekskill_meteorite.composition_symb should be ("Ni-Fe")
    peekskill_meteorite.massOfRemnant_lb should be (27.33704 +- 0.00001)
    peekskill_meteorite.massOfRemnant_kg should be (12.4 +- 0.1)
    peekskill_meteorite.massOfRemnant_kg *= 0.95   // erosion
    peekskill_meteorite.massOfRemnant_lb should be (25.970188 +- 0.000001)
    wantsAstrolith(peekskill_meteorite)
    wantsMeteorite(peekskill_meteorite)
  }

  // next time around, make a Meteoroid and put it through its transitions
  it must "work for transitions" in {
    val peekskill_meteoroid = new Astrolith.Meteoroid("nickel-iron", 14.0)
    peekskill_meteoroid.composition_symb should be ("Ni-Fe")
    peekskill_meteoroid.orbitalVelocity_mi_h should be (31317.02 +- 0.01)
    wantsAstrolith(peekskill_meteoroid)
    wantsMeteoroid(peekskill_meteoroid)

    val peekskill_meteor = peekskill_meteoroid.toMeteor(718674480000L)
    peekskill_meteor.composition_symb should be ("Ni-Fe")
    peekskill_meteor.impactDate_year should be (1992)
    peekskill_meteor.impactDate_ts should be (718674480000L)
    wantsAstrolith(peekskill_meteor)
    wantsMeteor(peekskill_meteor)

    val peekskill_meteorite = peekskill_meteor.toMeteorite(12.4)
    peekskill_meteorite.composition_symb should be ("Ni-Fe")
    peekskill_meteorite.massOfRemnant_lb should be (27.33704 +- 0.00001)
    peekskill_meteorite.massOfRemnant_kg should be (12.4 +- 0.1)
    peekskill_meteorite.massOfRemnant_kg *= 0.95   // erosion
    peekskill_meteorite.massOfRemnant_lb should be (25.970188 +- 0.000001)
    wantsAstrolith(peekskill_meteorite)
    wantsMeteorite(peekskill_meteorite)
  }
}
