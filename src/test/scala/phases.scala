package test.scala.phases

import org.junit.runner.RunWith

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers

import io.github.jpivarski.phases._
import test.scala._

@RunWith(classOf[JUnitRunner])
class PhasesSuite extends FlatSpec with Matchers {
  @stateMachine
  class Engine(@phase(Startup) config: String, @phase(Run) data: Int) {
    val always = 12

    @phase(Startup, Run)
    val always2 = 12

    @phase(Startup)
    val myConfig = config

    @phase(Run)
    val myData: Int = data

    @phase(Run)
    def whatsMyData(query: String): Int = myData

    // @transition(Startup, Run)
    // def startRun(data: Int): Engine.Run = new Engine.Run(data)
  }

  "test" must "do something" in {
    // val engine = new Engine.Startup
    val engine = new Engine("hello", 3)
    println(engine.myConfig)
    println(engine.myData)
    println(engine.whatsMyData("hey"))
  }
}
