package test.scala.phases

import org.junit.runner.RunWith

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers

import io.github.jpivarski.phases._
import test.scala._

@RunWith(classOf[JUnitRunner])
class PhasesSuite extends FlatSpec with Matchers {
  "test" must "do something" in {
    println("runtime")

    @identity class C

    @identity class D
    object D

    class E
    @identity object E

    def twice[@identity T](@identity x: Int) = x * 2
  }
}
