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

    // println(classDef)

    class ClassDefFilterer(phaseToKeep: Option[String]) extends Transformer {
      val getPhases = {
        case Apply(Select(New(x), _), Nil) if (phaseNames contains x.toString) => x.toString
      }: PartialFunction[Tree, String]

      override def transform(tree: Tree): Tree = tree match {
        case ValDef(mods, name, tpt, rhs) =>
          val phases = mods.annotations collect getPhases
          val otherAnnotations = mods.annotations filter {!getPhases.isDefinedAt(_)}

          val modified = ValDef(
            transformModifiers(Modifiers(mods.flags, mods.privateWithin, otherAnnotations)),
            name,
            transform(tpt),
            transform(rhs))

          phaseToKeep match {
            case None =>
              if (phases.isEmpty)
                modified
              else
                EmptyTree
            case Some(phase) =>
              if (phases contains phase)
                modified
              else
                EmptyTree
          }

        case DefDef(mods, name, tparams, vparamss, tpt, rhs) =>
          val phases = mods.annotations collect getPhases
          val otherAnnotations = mods.annotations filter {!getPhases.isDefinedAt(_)}
          
          val modified = DefDef(
            transformModifiers(Modifiers(mods.flags, mods.privateWithin, otherAnnotations)),
            name,
            transformTypeDefs(tparams),
            vparamss map {_ map {case ValDef(m, n, t, r) => ValDef(transformModifiers(m), n, transform(t), transform(r))}},
            transform(tpt),
            transform(rhs))

          phaseToKeep match {
            case None =>
              if (phases.isEmpty)
                modified
              else
                EmptyTree
            case Some(phase) =>
              if (phases contains phase)
                modified
              else
                EmptyTree
          }

        // case Template(x, ValDef(m, n, t, r), z) =>
          // println(x.getClass.getName)
          // println(x)
          // println(y.getClass.getName)
          // println(y)
          // println(z.getClass.getName)
          // println(z)
          // println()
          // Template(x map {transform(_)}, ValDef(m, n, transform(t), transform(r)), z map {transform(_)})

        case _ => super.transform(tree)
      }
    }

    val superclassDef = (new ClassDefFilterer(None)).transform(classDef)

    // println(superclassDef)
    
    c.Expr[Any](Block(List(superclassDef), Literal(Constant(()))))
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
