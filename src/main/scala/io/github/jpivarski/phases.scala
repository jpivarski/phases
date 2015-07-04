package io.github.jpivarski

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.annotation.StaticAnnotation

package object phases {
  class identity extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro identity.impl
  }
  object identity {
    def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      val inputs = annottees.map(_.tree).toList
      val (annottee, expandees) = inputs match {
        case (param: ValDef) :: (rest @ (_ :: _)) => (param, rest)
        case (param: TypeDef) :: (rest @ (_ :: _)) => (param, rest)
        case _ => (EmptyTree, inputs)
      }
      println((annottee, expandees))
      val outputs = expandees
      c.Expr[Any](Block(outputs, Literal(Constant(()))))
    }
  }
}
