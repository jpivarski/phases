package io.github.jpivarski

import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.Context
import scala.annotation.StaticAnnotation

package object phases {
  // only one annotation/macro in this package: "declare"; the rest are fake (syntax-only) annotations that get removed before compilation

  class declare extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro declare_impl
  }

  def declare_impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    // incidentally, this is a purely functional algorithm... it just worked out that way

    // get (what we think is the) class definition and maybe its companion object (for static declarations)
    val companionPair = annottees.map(_.tree).toList
    val classDefList = companionPair collect {case x: ClassDef => x}

    // make sure this annotation is applied to a class
    if (classDefList.size != 1)
      c.error(c.enclosingPosition, "@phases.declare can only be used on classes")
    val classDef = classDefList.head
    val ClassDef(_, className, _, _) = classDef

    // if there's no companion object, make one
    val companionObjectDef = companionPair.collect({case x: ModuleDef => x}).
      headOption.getOrElse(q"object ${newTermName(className.toString)}")

    // walk through the annotation's arguments to find the transitions
    // they should have the form State1 -> State2 and always go between distinct states (no reflexive)
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

    // the phase names are distinct beginning or endpoints of the transitions
    // by the above logic, we know that there are at least two of these
    val phaseNames = (transitions.map(_._1) ++ transitions.map(_._2)).distinct

    // ClassParam and classParams keep track of the class's parameter list for later use in making transition functions (only; not used for anything else)
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

    // for making transition functions between phases (this is MORE difficult to express as a quasiquote than a raw AST)
    def makeTransitionMethod(from: String, to: String): Tree = {
      val methodArgs = classParams filter {x => x.has(to)  &&  !x.has(from)} map {_.toParameter}
      val constructorArgs = classParams filter {x => x.has(to)} map {_.toArgument}
      DefDef(Modifiers(), newTermName("to" + to), List(), List(methodArgs), TypeTree(), Apply(Select(New(Select(Ident(newTermName(className.toString)), newTypeName(to))), nme.CONSTRUCTOR), constructorArgs))
    }

    // the main Transformer class for removing fake annotations and definitions that are (fake-)annotated for a subclass
    // when phasesToKeep is None, we're building the superclass (no phases)
    // when phasesToKeep is Some(phase), we're building a subclass (one phase)
    class ClassDefTransformer(phaseToKeep: Option[String]) extends Transformer {
      val getPhases = {
        case Apply(Select(New(x), _), Nil) if (phaseNames contains x.toString) => x.toString
      }: PartialFunction[Tree, String]

      // ValDefs can be parameters, vals, or vars; this function handles a variety of cases with boolean switches
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

      // the <init> methods for subclasses are very simple; however, this is still easier to express as an AST than a quasiquote
      def transformInit(tree: Tree, args: List[String]): Tree = tree match {
        case Block(Apply(fun, _) :: Nil, expr) => transform(Block(Apply(fun, args map {x => Ident(newTermName(x))}) :: Nil, expr))
        case x => transform(tree)
      }

      // the main transform function
      override def transform(tree: Tree): Tree = tree match {
        // several things happen to the ClassDef:
        //   (1) constructor code (all non ValDef at this level) is removed from subclasses (to avoid double-execution when they call their superclass's constructor)
        //   (2) transition methods are added
        //   (3) subclasses get named after the phase they represent
        //   (4) subclasses point to the superclass as their parent
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
              case None => name                       // the superclass should keep its original name
              case Some(phase) => newTypeName(phase)  // the subclasses should be named after their phase
            },
            transformTypeDefs(tparams),
            Template(phaseToKeep match {
              case None => parents                    // the superclass should keep its original list of superclasses
              case Some(phase) => List(Ident(name))   // the subclasses should point to the superclass
            }, transformValDef(self), transformedBody))

        // the ValDef case is already handled in the transformValDef method above
        case x: ValDef => transformValDef(x, true, phaseToKeep != None)

        // DefDefs are function definitions, including the constructor (named <init>).
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

        // all other cases refer to the general Transformer for a simple walk
        case _ => super.transform(tree)
      }
    }

    // transform the original class to make the superclass
    val superclassDef = (new ClassDefTransformer(None)).transform(classDef)

    // transform the original class to make subclasses, one for each phase
    val phaseDefs = phaseNames map {n => (new ClassDefTransformer(Some(n))).transform(classDef)}

    // insert the subclasses into the companion object so that they are statically declared
    val companionWithSubclasses = {
      val ModuleDef(mods, name, Template(parents, self, body)) = companionObjectDef
      ModuleDef(mods, name, Template(parents, self, body ++ phaseDefs))
    }

    // // optionally print out what we've done
    // println(superclassDef)
    // println(companionWithSubclasses)

    // and send it to the Scala compiler
    c.Expr[Any](Block(List(superclassDef, companionWithSubclasses), Literal(Constant(()))))
  }
}
