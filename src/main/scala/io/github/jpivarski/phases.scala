package io.github.jpivarski

import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.Context
import scala.annotation.StaticAnnotation

package object phases {
  class declare extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro declare_impl
  }

  def declare_impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    val classDef =
      annottees.map(_.tree).toList match {
        case (x: ClassDef) :: Nil => x
        case _ =>
          c.error(c.enclosingPosition, "@phases.declare can only be used on classes")
          throw new Error
      }

    val transitions =
      c.prefix.tree match {
        case Apply(_, args) => args map {
          case Apply(Select(x, y), z :: Nil) if (y.toString == "$minus$greater") =>
            val from = x.toString
            val to = z.toString
            if (from == to)
              c.error(c.enclosingPosition, "@phases.declare transition must point to two distinct states, not " + from + " -> " + to)
            (from, to)
          case _ =>
            c.error(c.enclosingPosition, "@phases.declare arguments must have the form STATE1 -> STATE2")
            throw new Error
        }
      }
    if (transitions.isEmpty)
      c.error(c.enclosingPosition, "@phases.declare requires at least one transition")

    val phaseNames = (transitions.map(_._1) ++ transitions.map(_._2)).distinct

    println(classDef)

    var superclassDef = classDef

    new Traverser {
      override def traverse(tree: Tree): Unit = tree match {
        case ValDef(mods, name, _, _) =>
          val phases = mods.annotations collect {
            case Apply(Select(New(x), _), Nil) if (phaseNames contains x.toString) => x.toString
          }
          println(name, phases)


          super.traverse(tree)
        case _ => super.traverse(tree)
      }
    } traverse(classDef)

    println(superclassDef)
    
    c.Expr[Any](Block(List(classDef), Literal(Constant(()))))
  }

  // def transition(from: Any, to: Any) = macro transition_impl
  // def transition_impl(c: Context)(from: Any, to: Any): c.Expr[Any] = {
  //   import c.universe._
  //   println(from, to)
  //   c.Expr[Any](Block(List(), Literal(Constant())))
  // }

  // class in extends StaticAnnotation {
  //   def macroTransform(annottees: Any*): Any = macro in.impl
  // }
  // object in {
  //   def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
  //     import c.universe._
  //     val expandees =
  //       annottees.map(_.tree).toList match {
  //         case (_: ValDef) :: (rest @ (_ :: _)) => rest
  //         case (_: TypeDef) :: (rest @ (_ :: _)) => rest
  //         case x => x
  //       }
  //     c.Expr[Any](Block(expandees, Literal(Constant(()))))
  //   }
  // }
}
