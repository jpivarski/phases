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

    val companionPair = annottees.map(_.tree).toList
    val classDefList = companionPair collect {case x: ClassDef => x}

    if (classDefList.size != 1)
      c.error(c.enclosingPosition, "@phases.declare can only be used on classes")
    val classDef = classDefList.head
    val ClassDef(_, className, _, _) = classDef

    val companionObjectDef = companionPair.collect({case x: ModuleDef => x}).
      headOption.getOrElse(q"object ${newTermName(className.toString)}")

    val transitions =
      c.prefix.tree match {
        case Apply(_, args) => args map {
          case Apply(Select(x, y), z :: Nil) if (y.toString == "$minus$greater") =>
            val from = x.toString
            val to = z.toString
            if (from == to)
              c.error(c.enclosingPosition, "@phases.declare transition must go between two distinct states, not " + from + " -> " + to)
            (from, to)
          case _ =>
            c.error(c.enclosingPosition, "@phases.declare arguments must have the form State1 -> State2")
            throw new Error
        }
      }
    if (transitions.isEmpty)
      c.error(c.enclosingPosition, "@phases.declare requires at least one transition")

    val phaseNames = (transitions.map(_._1) ++ transitions.map(_._2)).distinct

    case class ClassParam(flags: FlagSet, privateWithin: Name, partitionedAnnotations: (List[Tree], List[Tree]), paramName: TermName, tpt: Tree, rhs: Tree) {
      def has(phase: String) = (partitionedAnnotations._1.isEmpty)  ||  (partitionedAnnotations._1 exists {
        case Apply(Select(New(x), _), Nil) if (phase == x.toString) => true
        case _ => false
      })
      def toParameter = ValDef(Modifiers(flags, privateWithin, partitionedAnnotations._2), paramName, tpt, rhs)
      def toArgument = Ident(paramName)
    }

    val classParams = {
      val ClassDef(_, _, _, Template(_, _, body)) = classDef
      body collect {case DefDef(_, methodName, _, vparamss, _, _) if (methodName.toString == "<init>") =>
        vparamss flatMap {_ collect {case ValDef(mods, paramName, tpt, rhs) =>
          ClassParam(
            mods.flags,
            mods.privateWithin,
            mods.annotations partition {
              case Apply(Select(New(x), _), Nil) if (phaseNames contains x.toString) => true
              case _ => false
            },
            paramName,
            tpt,
            rhs)
        }}} flatten
    }

    def makeTransitionMethod(from: String, to: String): Tree = {
      val methodArgs = classParams filter {x => x.has(to)  &&  !x.has(from)} map {_.toParameter}
      val constructorArgs = classParams filter {x => x.has(to)} map {_.toArgument}
      DefDef(Modifiers(), newTermName("to" + to), List(), List(methodArgs), TypeTree(), Apply(Select(New(Select(Ident(newTermName(className.toString)), newTypeName(to))), nme.CONSTRUCTOR), constructorArgs))
    }

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
        case ClassDef(mods, name, tparams, Template(parents, self, body)) =>
          val basicTransform = body map {transform(_)} filter {_ != EmptyTree}

          val transformedBody = phaseToKeep match {
            case Some(phase) =>
              val transitionMethods = transitions filter {_._1 == phase} map {case (from, to) => makeTransitionMethod(from, to)}
              // drop everything but the field and methods declarations (to execute constructor code in superclass only once)
              // and then add transition metods
              (basicTransform collect {case x: ValDef => x; case x: DefDef => x}) ++ transitionMethods
            case None =>
              basicTransform
          }

          ClassDef(
            transformModifiers(mods),
            phaseToKeep match {
              case None => name
              case Some(phase) => newTypeName(phase)
            },
            transformTypeDefs(tparams),
            Template(phaseToKeep match {
              case None => parents
              case Some(phase) => List(Ident(name))
            }, transformValDef(self), transformedBody))

        case x: ValDef => transformValDef(x, true, phaseToKeep != None)

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

    val companionWithSubclasses = {
      val ModuleDef(mods, name, Template(parents, self, body)) = companionObjectDef
      ModuleDef(mods, name, Template(parents, self, body ++ phaseDefs))
    }

    println(superclassDef)
    println(companionWithSubclasses)

    c.Expr[Any](Block(List(superclassDef, companionWithSubclasses), Literal(Constant(()))))
  }
}
