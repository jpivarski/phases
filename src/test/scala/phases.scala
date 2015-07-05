package test.scala.phases

import org.junit.runner.RunWith

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers

import io.github.jpivarski.phases
import test.scala._

@RunWith(classOf[JUnitRunner])
class PhasesSuite extends FlatSpec with Matchers {
  @phases.declare(Meteoroid -> Meteor, Meteor -> Meteorite)
  class Astrolith(composition: String, @Meteoroid orbitalVelocity_km_s: Double, @Meteor impactDate_ts: Long, @Meteorite massOfRemnant_kg: Double) {
    def composition_symb = composition.replace("nickel", "Ni").replace("iron", "Fe")

    @Meteoroid
    def orbitalVelocity_mi_h = 2236.93 * orbitalVelocity_km_s

    @Meteor
    def impactDate_year = impactDate_ts / (365L * 24L * 60L * 60L * 1000L) + 1970

    @Meteorite
    def massOfRemnant_lb = 2.2046 * massOfRemnant_kg
  }

  "test" must "do something" in {
    val peekskill_astrolith = new Astrolith("nickel-iron")
    peekskill_astrolith.composition_symb should be ("Ni-Fe")

    val peekskill_meteoroid = new peekskill_astrolith.Meteoroid("nickel-iron", 14.0)
    peekskill_meteoroid.orbitalVelocity_mi_h should be (31317.02 +- 0.01)

    val peekskill_meteor = new peekskill_astrolith.Meteor("nickel-iron", 718674480000L)
    peekskill_meteor.impactDate_year should be (1992)

    val peekskill_meteorite = new peekskill_astrolith.Meteorite("nickel-iron", 12.4)
    peekskill_meteorite.massOfRemnant_lb should be (27.33704 +- 0.00001)

  }
}
