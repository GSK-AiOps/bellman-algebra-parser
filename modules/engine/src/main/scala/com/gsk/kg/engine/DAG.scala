package com.gsk.kg.engine

import higherkindness.droste._
import higherkindness.droste.data.Fix
import higherkindness.droste.macros.deriveTraverse
import higherkindness.droste.syntax.embed._
import higherkindness.droste.util.DefaultTraverse

import com.gsk.kg.sparqlparser.StringVal.VARIABLE
import com.gsk.kg.sparqlparser.StringVal

import cats.Traverse
import cats.Applicative
import cats.implicits._
import com.gsk.kg.sparqlparser.Expr
import com.gsk.kg.sparqlparser.Expr.fixedpoint._
import com.gsk.kg.sparqlparser.Query
import com.gsk.kg.sparqlparser.Expression

sealed trait DAG[A] {

  def rewrite(
      pf: PartialFunction[DAG[A], DAG[A]]
  )(implicit A: Basis[DAG, A]): A =
    scheme
      .cata(Trans(pf.orElse(PartialFunction[DAG[A], DAG[A]] { a =>
        a
      })).algebra)
      .apply(this.embed)

}

object DAG {
  final case class Describe[A](vars: List[VARIABLE], r: A) extends DAG[A]
  final case class Ask[A](r: A) extends DAG[A]
  final case class Construct[A](bgp: Expr.BGP, r: A) extends DAG[A]
  final case class Scan[A](graph: String, expr: A) extends DAG[A]
  final case class Project[A](variables: List[VARIABLE], r: A) extends DAG[A]
  final case class Bind[A](variable: VARIABLE, expression: Expression, r: A)
      extends DAG[A]
  final case class Triple[A](s: StringVal, p: StringVal, o: StringVal)
      extends DAG[A]
  final case class BGP[A](triples: List[A]) extends DAG[A]
  final case class LeftJoin[A](l: A, r: A, filters: List[Expression])
      extends DAG[A]
  final case class Union[A](l: A, r: A) extends DAG[A]
  final case class Filter[A](funcs: List[Expression], expr: A)
      extends DAG[A]
  final case class Join[A](l: A, r: A) extends DAG[A]
  final case class OffsetLimit[A](
      offset: Option[Long],
      limit: Option[Long],
      r: A
  ) extends DAG[A]
  final case class Distinct[A](r: A) extends DAG[A]
  final case class Noop[A](trace: String) extends DAG[A]

  implicit val traverse: Traverse[DAG] = new DefaultTraverse[DAG] {
    def traverse[G[_]: Applicative, A, B](fa: DAG[A])(f: A => G[B]): G[DAG[B]] =
      fa match {
        case DAG.Describe(vars, r)     => f(r).map(describe(vars, _))
        case DAG.Ask(r)                => f(r).map(ask)
        case DAG.Construct(bgp, r)     => f(r).map(construct(bgp, _))
        case DAG.Scan(graph, expr)     => f(expr).map(scan(graph, _))
        case DAG.Project(variables, r) => f(r).map(project(variables, _))
        case DAG.Bind(variable, expression, r) =>
          f(r).map(bind(variable, expression, _))
        case DAG.Triple(s, p, o) => triple(s, p, o).pure[G]
        case DAG.BGP(triples)    => triples.traverse(f).map(bgp)
        case DAG.LeftJoin(l, r, filters) =>
          (
            f(l),
            f(r)
          ).mapN(leftJoin(_, _, filters))
        case DAG.Union(l, r) => (f(l), f(r)).mapN(union)
        case DAG.Filter(funcs, expr) =>
          f(expr).map(filter(funcs, _))
        case DAG.Join(l, r) => (f(l), f(r)).mapN(join)
        case DAG.OffsetLimit(offset, limit, r) =>
          f(r).map(offsetLimit(offset, limit, _))
        case DAG.Distinct(r) => f(r).map(distinct)
        case DAG.Noop(str) => noop(str).pure[G]
      }
  }

  // Smart constructors for better type inference (they return DAG[A] instead of the case class itself)
  def describe[A](vars: List[VARIABLE], r: A): DAG[A] = Describe[A](vars, r)
  def ask[A](r: A): DAG[A] = Ask[A](r)
  def construct[A](bgp: Expr.BGP, r: A): DAG[A] = Construct[A](bgp, r)
  def scan[A](graph: String, expr: A): DAG[A] = Scan[A](graph, expr)
  def project[A](variables: List[VARIABLE], r: A): DAG[A] =
    Project[A](variables, r)
  def bind[A](variable: VARIABLE, expression: Expression, r: A): DAG[A] =
    Bind[A](variable, expression, r)
  def triple[A](s: StringVal, p: StringVal, o: StringVal): DAG[A] =
    Triple[A](s, p, o)
  def bgp[A](triples: List[A]): DAG[A] = BGP[A](triples)
  def leftJoin[A](l: A, r: A, filters: List[Expression]): DAG[A] =
    LeftJoin[A](l, r, filters)
  def union[A](l: A, r: A): DAG[A] = Union[A](l, r)
  def filter[A](funcs: List[Expression], expr: A): DAG[A] =
    Filter[A](funcs, expr)
  def join[A](l: A, r: A): DAG[A] = Join[A](l, r)
  def offsetLimit[A](offset: Option[Long], limit: Option[Long], r: A): DAG[A] =
    OffsetLimit[A](offset, limit, r)
  def distinct[A](r: A): DAG[A] = Distinct[A](r)
  def noop[A](trace: String): DAG[A] = Noop[A](trace)

  // Smart constructors for building the recursive version directly
  def describeR[T: Embed[DAG, *]](vars: List[VARIABLE], r: T): T =
    describe[T](vars, r).embed
  def askR[T: Embed[DAG, *]](r: T): T = ask[T](r).embed
  def constructR[T: Embed[DAG, *]](bgp: Expr.BGP, r: T): T = construct[T](bgp, r).embed
  def scanR[T: Embed[DAG, *]](graph: String, expr: T): T =
    scan[T](graph, expr).embed
  def projectR[T: Embed[DAG, *]](variables: List[VARIABLE], r: T): T =
    project[T](variables, r).embed
  def bindR[T: Embed[DAG, *]](
      variable: VARIABLE,
      expression: Expression,
      r: T
  ): T = bind[T](variable, expression, r).embed
  def tripleR[T: Embed[DAG, *]](s: StringVal, p: StringVal, o: StringVal): T =
    triple[T](s, p, o).embed
  def bgpR[T: Embed[DAG, *]](triples: List[T]): T = bgp[T](triples).embed
  def leftJoinR[T: Embed[DAG, *]](
      l: T,
      r: T,
      filters: List[Expression]
  ): T = leftJoin[T](l, r, filters).embed
  def unionR[T: Embed[DAG, *]](l: T, r: T): T = union[T](l, r).embed
  def filterR[T: Embed[DAG, *]](funcs: List[Expression], expr: T): T =
    filter[T](funcs, expr).embed
  def joinR[T: Embed[DAG, *]](l: T, r: T): T = join[T](l, r).embed
  def offsetLimitR[T: Embed[DAG, *]](
      offset: Option[Long],
      limit: Option[Long],
      r: T
  ): T = offsetLimit[T](offset, limit, r).embed
  def distinctR[T: Embed[DAG, *]](r: T): T = distinct[T](r).embed
  def noopR[T: Embed[DAG, *]](trace: String): T = noop[T](trace).embed


  /**
    * Transform a [[Query]] into its [[Fix[DAG]]] representation
    *
    * @param query
    * @return
    */
  def fromQuery[T: Embed[DAG, *]](query: Query): T = {
    lazy val transExpr: Trans[ExprF, DAG, T] = Trans {
      case ExtendF(bindTo, bindFrom, r)   => bind(bindTo, bindFrom, r)
      case FilteredLeftJoinF(l, r, f)     => leftJoin(l, r, f.toList)
      case UnionF(l, r)                   => union(l, r)
      case BGPF(triples)                  => bgp(triples.toList.map(convert))
      case OpNilF()                       => noop("OpNilF not supported yet")
      case GraphF(g, e)                   => scan(g.s, e)
      case JoinF(l, r)                    => join(l, r)
      case LeftJoinF(l, r)                => leftJoin(l, r, Nil)
      case ProjectF(vars, r)              => project(vars.toList, r)
      case TripleF(s, p, o)               => triple(s, p, o)
      case DistinctF(r)                   => distinct(r)
      case OffsetLimitF(offset, limit, r) => offsetLimit(offset, limit, r)
      case FilterF(funcs, expr)           => filter(funcs.toList, expr)
      case TabUnitF()                     => noop("TabUnitF not supported yet")
    }

    lazy val convert = scheme.cata(transExpr.algebra)

    query match {
	    case Query.Describe(vars, r) => describeR(vars.toList, convert(r))
	    case Query.Ask(r) => askR(convert(r))
	    case Query.Construct(vars, bgp, r) => constructR(bgp, convert(r))
	    case Query.Select(vars, r) => projectR(vars.toList, convert(r))
    }
  }

}
