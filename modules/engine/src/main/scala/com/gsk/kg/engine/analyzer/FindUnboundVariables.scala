package com.gsk.kg.engine
package analyzer

import cats.implicits._
import cats.{Group => _, _}

import higherkindness.droste.{Project => _, _}

import com.gsk.kg.engine.DAG._
import com.gsk.kg.engine.ExpressionF._
import com.gsk.kg.engine.data.ChunkedList
import com.gsk.kg.sparqlparser.Expression
import com.gsk.kg.sparqlparser.StringVal.GRAPH_VARIABLE
import com.gsk.kg.sparqlparser.StringVal.VARIABLE

/** This rule performs a bottom-up traverse of the DAG (with a
  * [[higherkindness.droste.Algebra]]), carrying the bound and unbound vars
  *
  * When arriving to the nodes that may use unbound variables
  * ([[DAG.Project]] and [[DAG.Construct]]), it compares the variables
  * used in that node with those that were declared beforehand in the
  * graph.  If there are any that are used but not declared, they're
  * returned and later on reported.
  */
object FindUnboundVariables {

  type DeclaredVars = Set[VARIABLE]
  type UnboundVars  = Set[VARIABLE]

  def apply[T](implicit T: Basis[DAG, T]): Rule[T] = { t =>
    val analyze =
      scheme.cata[DAG, T, (DeclaredVars, UnboundVars)](findUnboundVariables)

    val unbound: Set[VARIABLE] = analyze(t)._2
      .filterNot(v =>
        v == VARIABLE(GRAPH_VARIABLE.s) || isJenaAutogeneratedVariable(v)
      )

    if (unbound.nonEmpty) {
      val msg = "found free variables " + unbound.mkString(", ")

      msg.invalidNec
    } else {
      "ok".validNec
    }
  }

  private def isJenaAutogeneratedVariable(v: VARIABLE): Boolean =
    v.s.replace("?", "").forall(_.isDigit)

  val findUnboundVariables: Algebra[DAG, (DeclaredVars, UnboundVars)] =
    Algebra[DAG, (DeclaredVars, UnboundVars)] {
      case Describe(values, (declared, unbound)) =>
        val vars: Set[VARIABLE] =
          values
            .filter({
              case VARIABLE(_) => true
              case _ => false
            })
            .map(variable => variable.asInstanceOf[VARIABLE])
            .toSet

        (declared, (vars diff declared) ++ unbound)
      case Ask((declared, unbound)) => (declared, unbound)
      case Construct(bgp, (declared, unbound)) =>
        val used = bgp.quads
          .flatMap(_.getVariables)
          .map(_._1.asInstanceOf[VARIABLE])
          .toSet

        (declared, (used diff declared) ++ unbound)
      case Scan(graph, (declared, unbound)) =>
        (declared + VARIABLE(graph), unbound)
      case Project(variables, (declared, unbound)) =>
        (variables.toSet, (variables.toSet diff declared) ++ unbound)
      case Bind(variable, expression, (declared, unbound)) =>
        (declared + variable, unbound)
      case BGP(triples) =>
        val vars = Traverse[ChunkedList]
          .toList(triples)
          .flatMap(_.getVariables)
          .map(_._1.asInstanceOf[VARIABLE])
          .toSet

        (vars, Set.empty)
      case LeftJoin((declaredL, unboundL), (declaredR, unboundR), filters) =>
        (declaredL ++ declaredR, unboundL ++ unboundR)
      case Union((declaredL, unboundL), (declaredR, unboundR)) =>
        (declaredL ++ declaredR, unboundL ++ unboundR)
      case Minus((declaredL, unboundL), (declaredR, unboundR)) =>
        (declaredL ++ declaredR, unboundL ++ unboundR)
      case Filter(funcs, (declared, unbound)) =>
        val funcsVars = funcs.toList.toSet
          .foldLeft(Set.empty[VARIABLE]) { case (acc, func) =>
            acc ++ FindVariablesOnExpression.apply[Expression](func)
          }
        (declared, (funcsVars diff declared) ++ unbound)
      case Join((declaredL, unboundL), (declaredR, unboundR)) =>
        (declaredL ++ declaredR, unboundL ++ unboundR)
      case Offset(offset, r) => r
      case Limit(limit, r)   => r
      case Distinct(r)       => r
      case Reduced(r)        => r
      case Group(vars, func, (declared, unbound)) =>
        (declared, (vars.toSet diff declared) ++ unbound)
      case DAG.Order(conds, (declared, unbound)) =>
        val condVars = conds.toList.toSet
          .foldLeft(Set.empty[VARIABLE]) { case (acc, cond) =>
            acc ++ FindVariablesOnExpression.apply[Expression](cond)
          }

        (declared, (condVars diff declared) ++ unbound)
      case Exists(_, _, r) => r
      case Table(vars, rows) =>
        (vars.toSet, Set.empty)
      case Noop(trace) =>
        (Set.empty, Set.empty)
    }
}

object FindVariablesOnExpression {

  def apply[T](t: T)(implicit T: Basis[ExpressionF, T]): Set[VARIABLE] = {
    val algebra: Algebra[ExpressionF, Set[VARIABLE]] =
      Algebra[ExpressionF, Set[VARIABLE]] {
        case EQUALS(l, r)                    => l ++ r
        case GT(l, r)                        => l ++ r
        case LT(l, r)                        => l ++ r
        case GTE(l, r)                       => l ++ r
        case LTE(l, r)                       => l ++ r
        case OR(l, r)                        => l ++ r
        case AND(l, r)                       => l ++ r
        case NEGATE(s)                       => s
        case IN(e, xs)                       => e ++ xs.flatten
        case SAMETERM(l, r)                  => l ++ r
        case IF(cnd, ifTrue, ifFalse)        => cnd ++ ifTrue ++ ifFalse
        case BOUND(e)                        => e
        case COALESCE(xs)                    => xs.toSet.flatten
        case URI(s)                          => s
        case REGEX(s, pattern, flags)        => s
        case REPLACE(st, pattern, by, flags) => st
        case STRENDS(s, f)                   => s
        case STRSTARTS(s, f)                 => s
        case CONCAT(appendTo, append)        => appendTo ++ append.toList.toSet.flatten
        case STR(s)                          => s
        case STRAFTER(s, f)                  => s
        case STRBEFORE(s, f)                 => s
        case STRDT(s, uri)                   => s
        case SUBSTR(s, pos, len)             => s
        case STRLEN(s)                       => s
        case ISBLANK(s)                      => s
        case ISNUMERIC(s)                    => s
        case COUNT(e)                        => e
        case SUM(e)                          => e
        case MIN(e)                          => e
        case MAX(e)                          => e
        case AVG(e)                          => e
        case SAMPLE(e)                       => e
        case LANG(e)                         => e
        case LANGMATCHES(e, range)           => e
        case LCASE(e)                        => e
        case UCASE(e)                        => e
        case ISLITERAL(e)                    => e
        case GROUP_CONCAT(e, separator)      => e
        case ENCODE_FOR_URI(s)               => s
        case MD5(s)                          => s
        case SHA1(s)                         => s
        case SHA256(s)                       => s
        case SHA384(s)                       => s
        case SHA512(s)                       => s
        case STRING(s)                       => Set.empty[VARIABLE]
        case DT_STRING(s, tag)               => Set.empty[VARIABLE]
        case LANG_STRING(s, tag)             => Set.empty[VARIABLE]
        case NUM(s)                          => Set.empty[VARIABLE]
        case ExpressionF.VARIABLE(s)         => Set(VARIABLE(s))
        case URIVAL(s)                       => Set.empty[VARIABLE]
        case BLANK(s)                        => Set.empty[VARIABLE]
        case BOOL(s)                         => Set.empty[VARIABLE]
        case ASC(e)                          => e
        case DESC(e)                         => e
      }

    val eval =
      scheme.cata[ExpressionF, T, Set[VARIABLE]](algebra)

    eval(t)
  }
}
