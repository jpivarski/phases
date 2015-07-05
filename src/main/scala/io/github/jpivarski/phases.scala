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

    class ClassDefFilterer(phaseToKeep: Option[String]) extends Transformer {
      val getPhases = {
        case Apply(Select(New(x), _), Nil) if (phaseNames contains x.toString) => x.toString
      }: PartialFunction[Tree, String]

      def transformValDef(valDef: ValDef, includeGeneral: Boolean, includeSpecific: Boolean): Tree = {
        val phases = valDef.mods.annotations collect getPhases
        val otherAnnotations = valDef.mods.annotations filter {!getPhases.isDefinedAt(_)}
        
        val isGeneral = (phases.isEmpty  ||  phases.size == phaseNames.size)
        val isSpecific = phaseToKeep match {
          case None => false
          case Some(phase) => (!isGeneral  &&  phases.contains(phase))
        }

        if ((includeGeneral  &&  isGeneral)  ||  (includeSpecific  &&  isSpecific))
          ValDef(
            transformModifiers(Modifiers(valDef.mods.flags, valDef.mods.privateWithin, otherAnnotations)),
            valDef.name,
            transform(valDef.tpt),
            transform(valDef.rhs))
        else
          EmptyTree
      }

      def transformInit(tree: Tree, args: List[String]): Tree = tree match {
        case Block(Apply(fun, _) :: Nil, expr) => transform(Block(Apply(fun, args map {x => Ident(newTermName(x))}) :: Nil, expr))
        case x => transform(tree)
      }

      override def transform(tree: Tree): Tree = tree match {
        case ClassDef(mods, name, tparams, Template(parents, self, body)) => ClassDef(
          transformModifiers(mods),
          phaseToKeep match {
            case None => name
            case Some(phase) => newTypeName(phase)
          },
          transformTypeDefs(tparams),
          Template(phaseToKeep match {
            case None => parents
            case Some(phase) => List(Ident(name))
          }, transformValDef(self), body map {transform(_)} filter {_ != EmptyTree}))

        case x: ValDef => transformValDef(x, phaseToKeep == None, phaseToKeep != None)

        case DefDef(mods, name, tparams, vparamss, tpt, rhs) =>
          val phases = mods.annotations collect getPhases
          val otherAnnotations = mods.annotations filter {!getPhases.isDefinedAt(_)}
         
          val transformedVparamss = vparamss map {_ map {x => transformValDef(x, true, phaseToKeep != None)} collect {case y: ValDef => y}}

          val isGeneral = (phases.isEmpty  ||  phases.size == phaseNames.size)
          val isSpecific = phaseToKeep match {
            case None => false
            case Some(phase) => (!isGeneral  &&  phases.contains(phase))
          }

          if (name.toString == "<init>"  ||  (phaseToKeep == None  &&  isGeneral)  ||  (phaseToKeep != None  &&  isSpecific))
            DefDef(
              transformModifiers(Modifiers(mods.flags, mods.privateWithin, otherAnnotations)),
              name,
              transformTypeDefs(tparams),
              transformedVparamss,
              transform(tpt),
              if (name.toString == "<init>"  &&  phaseToKeep != None)
                transformInit(rhs, vparamss flatMap {_ map {transformValDef(_, true, false)} collect {case ValDef(_, n, _, _) => n.toString}})
              else
                transform(rhs))
          else
            EmptyTree

        case _ => super.transform(tree)
      }
    }

    val superclassDef = (new ClassDefFilterer(None)).transform(classDef)
    val phaseDefs = phaseNames map {n => (new ClassDefFilterer(Some(n))).transform(classDef)}

    val superclassDefWithPhases = {
      val ClassDef(mods, name, tparams, Template(parents, self, body)) = superclassDef
      ClassDef(mods, name, tparams, Template(parents, self, body ++ phaseDefs))
    }

    println(superclassDefWithPhases)

    c.Expr[Any](Block(List(superclassDefWithPhases), Literal(Constant(()))))
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
