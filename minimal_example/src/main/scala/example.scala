import io.github.jpivarski.phases

@phases.declare(meteoroid -> meteor, meteor -> meteorite)(debug = true)
class Astrolith(composition: String,
                @meteoroid orbitalVelocity_km_s: Double,
                @meteor val impactDate_ts: Long,
                @meteorite var massOfRemnant_kg: Double) {

  def composition_symb = composition.replace("nickel", "Ni").replace("iron", "Fe")

  @meteoroid
  def orbitalVelocity_mi_h = 2236.93 * orbitalVelocity_km_s

  @meteor
  def impactDate_year = impactDate_ts / (365L * 24L * 60L * 60L * 1000L) + 1970

  @meteorite
  def massOfRemnant_lb = 2.2046 * massOfRemnant_kg
}

object Main extends App {
  override def main(args: Array[String]) {
    val peekskill_meteoroid = new Astrolith.meteoroid("nickel-iron", 14.0)
    println(peekskill_meteoroid.composition_symb)
    println(peekskill_meteoroid.orbitalVelocity_mi_h)

    val peekskill_meteor = peekskill_meteoroid.toMeteor(718674480000L)
    println(peekskill_meteor.composition_symb)
    println(peekskill_meteor.impactDate_year)

    val peekskill_meteorite = peekskill_meteor.toMeteorite(12.4)
    println(peekskill_meteorite.composition_symb)
    println(peekskill_meteorite.massOfRemnant_lb)
  }
}
