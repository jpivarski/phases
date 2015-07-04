package io.github.jpivarski

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.annotation.StaticAnnotation

package phases {
  class stateMachine extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro stateMachine.impl
  }
  object stateMachine {
    def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      val inputs = annottees.map(_.tree).toList
      val expandees =
        inputs match {
          case (param: ValDef) :: _ =>
            c.error(c.enclosingPosition, "@stateMachine can only be used on classes")
            throw new Error
          case (param: TypeDef) :: _ =>
            c.error(c.enclosingPosition, "@stateMachine can only be used on classes")
            throw new Error
          case _ => inputs
        }
      println("stateMachine", expandees)
      c.Expr[Any](Block(expandees, Literal(Constant(()))))
    }
  }

  class phase extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro phase.impl
  }
  object phase {
    def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      val expandees =
        annottees.map(_.tree).toList match {
          case (_: ValDef) :: (rest @ (_ :: _)) => rest
          case (_: TypeDef) :: (rest @ (_ :: _)) => rest
          case x => x
        }
      c.Expr[Any](Block(expandees, Literal(Constant(()))))
    }
  }

  class transition extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro transition.impl
  }
  object transition {
    def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      val expandees =
        annottees.map(_.tree).toList match {
          case (_: ValDef) :: (rest @ (_ :: _)) => rest
          case (_: TypeDef) :: (rest @ (_ :: _)) => rest
          case x => x
        }
      c.Expr[Any](Block(expandees, Literal(Constant(()))))
    }
  }
}
