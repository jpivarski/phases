package test.scala.phases

import org.junit.runner.RunWith

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers

import io.github.jpivarski.phases
import test.scala._

@RunWith(classOf[JUnitRunner])
class PhasesSuite extends FlatSpec with Matchers {
  @phases.declare(Startup -> Run)
  class Engine(always: Int, @Startup config: String, @Run data: Int) {
    val myAlways = always

    @Startup @Run
    val myAlways2 = always

    @Startup
    val myConfig = config

    @Run
    val myData: Int = data

    @Run
    def whatsMyData(query: String): Int = myData

    // class Startup extends Engine(always, config, data)
    // class Run extends Engine(always, config, data)
  }

  "test" must "do something" in {
    // val engine = new Engine.Startup
    val engine = new Engine(12, "hello", 3)
    println(engine.myConfig)
    println(engine.myData)
    println(engine.whatsMyData("hey"))
  }
}
