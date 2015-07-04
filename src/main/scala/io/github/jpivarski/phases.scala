package io.github.jpivarski

import scala.language.experimental.macros
import scala.reflect.macros.Context

package object phases {
  def hello(): Unit = macro hello_impl
  def hello_impl(c: Context)(): c.Expr[Unit] = {
    import c.universe._
    reify {println("<<Bonjour, tout le monde!>>")}
  }

}
