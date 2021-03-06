package com.gsk.kg.engine

import cats._
import cats.data.NonEmptyList
import cats.implicits._

import higherkindness.droste._
import higherkindness.droste.syntax.all._
import higherkindness.droste.util.DefaultTraverse

import com.gsk.kg.engine.data.ChunkedList
import com.gsk.kg.sparqlparser.ConditionOrder
import com.gsk.kg.sparqlparser.Expr
import com.gsk.kg.sparqlparser.Expr.fixedpoint._
import com.gsk.kg.sparqlparser.Expression
import com.gsk.kg.sparqlparser.PropertyExpression
import com.gsk.kg.sparqlparser.Query
import com.gsk.kg.sparqlparser.StringVal
import com.gsk.kg.sparqlparser.StringVal.VARIABLE

import monocle._
import monocle.macros.Lenses

sealed trait DAG[A] {

  def rewrite(
      pf: PartialFunction[DAG[A], DAG[A]]
  )(implicit A: Basis[DAG, A]): A =
    scheme
      .cata(Trans(pf.orElse(PartialFunction[DAG[A], DAG[A]] { a =>
        a
      })).algebra)
      .apply(this.embed)

  def widen: DAG[A] = this
}

// scalastyle:off
object DAG {
  @Lenses final case class Describe[A](vars: List[StringVal], r: A)
      extends DAG[A]
  @Lenses final case class Ask[A](r: A)                      extends DAG[A]
  @Lenses final case class Construct[A](bgp: Expr.BGP, r: A) extends DAG[A]
  @Lenses final case class Scan[A](graph: String, expr: A)   extends DAG[A]
  @Lenses final case class Project[A](variables: List[VARIABLE], r: A)
      extends DAG[A]
  @Lenses final case class Bind[A](
      variable: VARIABLE,
      expression: Expression,
      r: A
  )                                                  extends DAG[A]
  @Lenses final case class Sequence[A](bps: List[A]) extends DAG[A]
  @Lenses final case class Path[A](
      s: StringVal,
      p: PropertyExpression,
      o: StringVal,
      g: List[StringVal]
  )                                                              extends DAG[A]
  @Lenses final case class BGP[A](quads: ChunkedList[Expr.Quad]) extends DAG[A]
  @Lenses final case class LeftJoin[A](l: A, r: A, filters: List[Expression])
      extends DAG[A]
  @Lenses final case class Union[A](l: A, r: A) extends DAG[A]
  @Lenses final case class Minus[A](l: A, r: A) extends DAG[A]
  @Lenses final case class Filter[A](funcs: NonEmptyList[Expression], expr: A)
      extends DAG[A]
  @Lenses final case class Join[A](l: A, r: A)           extends DAG[A]
  @Lenses final case class Offset[A](offset: Long, r: A) extends DAG[A]
  @Lenses final case class Limit[A](limit: Long, r: A)   extends DAG[A]
  @Lenses final case class Group[A](
      vars: List[VARIABLE],
      func: List[(VARIABLE, Expression)],
      r: A
  ) extends DAG[A]
  @Lenses final case class Order[A](conds: NonEmptyList[ConditionOrder], r: A)
      extends DAG[A]
  @Lenses final case class Distinct[A](r: A) extends DAG[A]
  @Lenses final case class Reduced[A](r: A)  extends DAG[A]
  @Lenses final case class Table[A](vars: List[VARIABLE], rows: List[Expr.Row])
      extends DAG[A]
  @Lenses final case class Exists[A](not: Boolean, p: A, r: A) extends DAG[A]
  @Lenses final case class Noop[A](trace: String)              extends DAG[A]

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
        case DAG.Sequence(bps) =>
          bps.map(f).sequence.map(DAG.sequence)
        case DAG.Path(s, p, o, g) => path[B](s, p, o, g).pure[G]
        case DAG.BGP(quads)       => bgp[B](quads).pure[G]
        case DAG.LeftJoin(l, r, filters) =>
          (
            f(l),
            f(r)
          ).mapN(leftJoin(_, _, filters))
        case DAG.Union(l, r) => (f(l), f(r)).mapN(union)
        case DAG.Minus(l, r) => (f(l), f(r)).mapN(minus)
        case DAG.Filter(funcs, expr) =>
          f(expr).map(filter(funcs, _))
        case DAG.Join(l, r) => (f(l), f(r)).mapN(join)
        case DAG.Offset(o, r) =>
          f(r).map(offset(o, _))
        case DAG.Limit(l, r) =>
          f(r).map(limit(l, _))
        case DAG.Distinct(r)          => f(r).map(distinct)
        case DAG.Reduced(r)           => f(r).map(reduced)
        case DAG.Group(vars, func, r) => f(r).map(group(vars, func, _))
        case DAG.Order(conds, r)      => f(r).map(order(conds, _))
        case DAG.Table(vars, rows)    => table[B](vars, rows).pure[G]
        case DAG.Exists(not, p, r)    => (f(p), f(r)).mapN(DAG.exists(not, _, _))
        case DAG.Noop(str)            => noop[B](str).pure[G]
      }
  }

  // Smart constructors for better type inference (they return DAG[A] instead of the case class itself)
  def describe[A](vars: List[StringVal], r: A): DAG[A] = Describe[A](vars, r)
  def ask[A](r: A): DAG[A]                             = Ask[A](r)
  def construct[A](bgp: Expr.BGP, r: A): DAG[A]        = Construct[A](bgp, r)
  def scan[A](graph: String, expr: A): DAG[A]          = Scan[A](graph, expr)
  def project[A](variables: List[VARIABLE], r: A): DAG[A] =
    Project[A](variables, r)
  def bind[A](variable: VARIABLE, expression: Expression, r: A): DAG[A] =
    Bind[A](variable, expression, r)
  def sequence[A](bps: List[A]): DAG[A] = Sequence[A](bps)
  def path[A](
      s: StringVal,
      p: PropertyExpression,
      o: StringVal,
      g: List[StringVal]
  ): DAG[A] =
    Path(s, p, o, g)
  def bgp[A](quads: ChunkedList[Expr.Quad]): DAG[A] = BGP[A](quads)
  def leftJoin[A](l: A, r: A, filters: List[Expression]): DAG[A] =
    LeftJoin[A](l, r, filters)
  def union[A](l: A, r: A): DAG[A] = Union[A](l, r)
  def minus[A](l: A, r: A): DAG[A] = Minus[A](l, r)
  def filter[A](funcs: NonEmptyList[Expression], expr: A): DAG[A] =
    Filter[A](funcs, expr)
  def join[A](l: A, r: A): DAG[A] = Join[A](l, r)
  def offset[A](offset: Long, r: A): DAG[A] =
    Offset[A](offset, r)
  def limit[A](limit: Long, r: A): DAG[A] =
    Limit[A](limit, r)
  def distinct[A](r: A): DAG[A] = Distinct[A](r)
  def reduced[A](r: A): DAG[A]  = Reduced[A](r)
  def group[A](
      vars: List[VARIABLE],
      func: List[(VARIABLE, Expression)],
      r: A
  ): DAG[A] =
    Group[A](vars, func, r)
  def order[A](conds: NonEmptyList[ConditionOrder], r: A): DAG[A] =
    Order[A](conds, r)
  def table[A](vars: List[VARIABLE], rows: List[Expr.Row]): DAG[A] =
    Table[A](vars, rows)
  def exists[A](not: Boolean, p: A, r: A): DAG[A] =
    Exists[A](not, p, r)
  def noop[A](trace: String): DAG[A] = Noop[A](trace)

  // Smart constructors for building the recursive version directly
  def describeR[T: Embed[DAG, *]](vars: List[StringVal], r: T): T =
    describe[T](vars, r).embed
  def askR[T: Embed[DAG, *]](r: T): T = ask[T](r).embed
  def constructR[T: Embed[DAG, *]](bgp: Expr.BGP, r: T): T =
    construct[T](bgp, r).embed
  def scanR[T: Embed[DAG, *]](graph: String, expr: T): T =
    scan[T](graph, expr).embed
  def projectR[T: Embed[DAG, *]](variables: List[VARIABLE], r: T): T =
    project[T](variables, r).embed
  def bindR[T: Embed[DAG, *]](
      variable: VARIABLE,
      expression: Expression,
      r: T
  ): T = bind[T](variable, expression, r).embed
  def sequenceR[T: Embed[DAG, *]](bps: List[T]): T =
    sequence[T](bps).embed
  def pathR[T: Embed[DAG, *]](
      s: StringVal,
      p: PropertyExpression,
      o: StringVal,
      g: List[StringVal]
  ): T =
    path[T](s, p, o, g).embed
  def bgpR[T: Embed[DAG, *]](triples: ChunkedList[Expr.Quad]): T =
    bgp[T](triples).embed
  def leftJoinR[T: Embed[DAG, *]](
      l: T,
      r: T,
      filters: List[Expression]
  ): T                                        = leftJoin[T](l, r, filters).embed
  def unionR[T: Embed[DAG, *]](l: T, r: T): T = union[T](l, r).embed
  def minusR[T: Embed[DAG, *]](l: T, r: T): T = minus[T](l, r).embed
  def filterR[T: Embed[DAG, *]](funcs: NonEmptyList[Expression], expr: T): T =
    filter[T](funcs, expr).embed
  def joinR[T: Embed[DAG, *]](l: T, r: T): T = join[T](l, r).embed
  def offsetR[T: Embed[DAG, *]](
      o: Long,
      r: T
  ): T = offset[T](o, r).embed
  def limitR[T: Embed[DAG, *]](
      l: Long,
      r: T
  ): T                                     = limit[T](l, r).embed
  def distinctR[T: Embed[DAG, *]](r: T): T = distinct[T](r).embed
  def reducedR[T: Embed[DAG, *]](r: T): T  = reduced[T](r).embed
  def groupR[T: Embed[DAG, *]](
      vars: List[VARIABLE],
      func: List[(VARIABLE, Expression)],
      r: T
  ): T = group[T](vars, func, r).embed
  def orderR[T: Embed[DAG, *]](
      conds: NonEmptyList[ConditionOrder],
      r: T
  ): T = order[T](conds, r).embed
  def tableR[T: Embed[DAG, *]](vars: List[VARIABLE], rows: List[Expr.Row]): T =
    table[T](vars, rows).embed
  def existsR[T: Embed[DAG, *]](not: Boolean, p: T, r: T): T =
    exists[T](not, p, r).embed
  def noopR[T: Embed[DAG, *]](trace: String): T = noop[T](trace).embed

  /** Transform a [[Query]] into its [[Fix[DAG]]] representation
    *
    * @param query
    * @return
    */
  def fromQuery[T: Basis[DAG, *]]: Query => T = {
    case Query.Describe(vars, r) =>
      describeR(vars.toList, fromExpr[T].apply(r))
    case Query.Ask(r) => askR(fromExpr[T].apply(r))
    case Query.Construct(vars, bgp, r) =>
      constructR(bgp, fromExpr[T].apply(r))
    case Query.Select(vars, r) =>
      projectR(vars.toList, fromExpr[T].apply(r))
  }

  def fromExpr[T: Basis[DAG, *]]: Expr => T = scheme.cata(transExpr.algebra)

  def transExpr[T](implicit T: Basis[DAG, T]): Trans[ExprF, DAG, T] =
    Trans {
      case SequenceF(bps)               => sequence(bps)
      case ExtendF(bindTo, bindFrom, r) => bind(bindTo, bindFrom, r)
      case FilteredLeftJoinF(l, r, f)   => leftJoin(l, r, f.toList)
      case UnionF(l, r)                 => union(l, r)
      case MinusF(l, r)                 => minus(l, r)
      case BGPF(quads)                  => bgp(ChunkedList.fromList(quads.toList))
      case OpNilF()                     => noop("OpNilF not supported yet")
      case GraphF(g, e)                 => scan(g.s, e)
      case JoinF(l, r)                  => join(l, r)
      case LeftJoinF(l, r)              => leftJoin(l, r, Nil)
      case ProjectF(vars, r)            => project(vars.toList, r)
      case PathF(s, p, o, g)            => path(s, p, o, g)
      case QuadF(s, p, o, g)            => noop("QuadF not supported")
      case DistinctF(r)                 => distinct(r)
      case ReducedF(r)                  => reduced(r)
      case GroupF(vars, func, r)        => group(vars.toList, func.toList, r)
      case OrderF(conds, r) =>
        order(NonEmptyList.fromListUnsafe(conds.toList), r)
      case OffsetLimitF(None, None, r)       => T.coalgebra(r)
      case OffsetLimitF(None, Some(l), r)    => limit(l, r)
      case OffsetLimitF(Some(o), None, r)    => offset(o, r)
      case OffsetLimitF(Some(o), Some(l), r) => offset(o, limit(l, r).embed)
      case FilterF(funcs, expr) =>
        filter(NonEmptyList.fromListUnsafe(funcs.toList), expr)
      case TableF(vars, rows) => table(vars.toList, rows.toList)
      case ExistsF(not, p, r) => exists(not, p, r)
      case RowF(tuples)       => noop("RowF not supported yet")
      case TabUnitF()         => noop("TabUnitF not supported yet")
    }

  implicit def dagEq[A: Eq]: Eq[DAG[A]] =
    Eq.fromUniversalEquals

  implicit val eqDelay: Delay[Eq, DAG] =
    λ[Eq ~> (Eq ∘ DAG)#λ] { eqA =>
      dagEq(eqA)
    }

  implicit def eqDescribe[A]: Eq[Describe[A]]   = Eq.fromUniversalEquals
  implicit def eqAsk[A]: Eq[Ask[A]]             = Eq.fromUniversalEquals
  implicit def eqConstruct[A]: Eq[Construct[A]] = Eq.fromUniversalEquals
  implicit def eqScan[A]: Eq[Scan[A]]           = Eq.fromUniversalEquals
  implicit def eqProject[A]: Eq[Project[A]]     = Eq.fromUniversalEquals
  implicit def eqBind[A]: Eq[Bind[A]]           = Eq.fromUniversalEquals
  implicit def eqSequence[A]: Eq[Sequence[A]]   = Eq.fromUniversalEquals
  implicit def eqPathQuad[A]: Eq[Path[A]]       = Eq.fromUniversalEquals
  implicit def eqBGP[A]: Eq[BGP[A]]             = Eq.fromUniversalEquals
  implicit def eqLeftJoin[A]: Eq[LeftJoin[A]]   = Eq.fromUniversalEquals
  implicit def eqUnion[A]: Eq[Union[A]]         = Eq.fromUniversalEquals
  implicit def eqMinus[A]: Eq[Minus[A]]         = Eq.fromUniversalEquals
  implicit def eqFilter[A]: Eq[Filter[A]]       = Eq.fromUniversalEquals
  implicit def eqJoin[A]: Eq[Join[A]]           = Eq.fromUniversalEquals
  implicit def eqOffset[A]: Eq[Offset[A]]       = Eq.fromUniversalEquals
  implicit def eqLimit[A]: Eq[Limit[A]]         = Eq.fromUniversalEquals
  implicit def eqGroup[A]: Eq[Group[A]]         = Eq.fromUniversalEquals
  implicit def eqOrder[A]: Eq[Order[A]]         = Eq.fromUniversalEquals
  implicit def eqDistinct[A]: Eq[Distinct[A]]   = Eq.fromUniversalEquals
  implicit def eqReduced[A]: Eq[Reduced[A]]     = Eq.fromUniversalEquals
  implicit def eqTable[A]: Eq[Table[A]]         = Eq.fromUniversalEquals
  implicit def eqExists[A]: Eq[Exists[A]]       = Eq.fromUniversalEquals
  implicit def eqNoop[A]: Eq[Noop[A]]           = Eq.fromUniversalEquals
}

object optics {
  import DAG._

  def basisIso[F[_], T](implicit T: Basis[F, T]): Iso[T, F[T]] = Iso[T, F[T]] {
    t => Basis[F, T].coalgebra(t)
  }(dag => Basis[F, T].algebra(dag))

  def _describe[T: Basis[DAG, *]]: Prism[DAG[T], Describe[T]] =
    Prism.partial[DAG[T], Describe[T]] { case dag @ Describe(vars, r) => dag }(
      identity
    )
  def _ask[T: Basis[DAG, *]]: Prism[DAG[T], Ask[T]] =
    Prism.partial[DAG[T], Ask[T]] { case dag @ Ask(r) => dag }(identity)
  def _construct[T: Basis[DAG, *]]: Prism[DAG[T], Construct[T]] =
    Prism.partial[DAG[T], Construct[T]] {
      case dag @ Construct(bgp: Expr.BGP, r) => dag
    }(identity)
  def _scan[T: Basis[DAG, *]]: Prism[DAG[T], Scan[T]] =
    Prism.partial[DAG[T], Scan[T]] { case dag @ Scan(graph: String, expr) =>
      dag
    }(identity)
  def _project[T: Basis[DAG, *]]: Prism[DAG[T], Project[T]] =
    Prism.partial[DAG[T], Project[T]] {
      case dag @ Project(variables: List[VARIABLE], r) => dag
    }(identity)
  def _bind[T: Basis[DAG, *]]: Prism[DAG[T], Bind[T]] =
    Prism.partial[DAG[T], Bind[T]] {
      case dag @ Bind(variable: VARIABLE, expression: Expression, r) => dag
    }(identity)
  def _sequence[T: Basis[DAG, *]]: Prism[DAG[T], Sequence[T]] =
    Prism.partial[DAG[T], Sequence[T]] { case dag @ Sequence(bps) =>
      dag
    }(identity)
  def _path[T: Basis[DAG, *]]: Prism[DAG[T], Path[T]] =
    Prism.partial[DAG[T], Path[T]] { case dag @ Path(s, p, o, g) =>
      dag
    }(identity)
  def _bgp[T: Basis[DAG, *]]: Prism[DAG[T], BGP[T]] =
    Prism.partial[DAG[T], BGP[T]] {
      case dag @ BGP(triples: ChunkedList[Expr.Quad]) => dag
    }(identity)
  def _leftjoin[T: Basis[DAG, *]]: Prism[DAG[T], LeftJoin[T]] =
    Prism.partial[DAG[T], LeftJoin[T]] {
      case dag @ LeftJoin(l, r, filters: List[Expression]) => dag
    }(identity)
  def _union[T: Basis[DAG, *]]: Prism[DAG[T], Union[T]] =
    Prism.partial[DAG[T], Union[T]] { case dag @ Union(l, r) => dag }(identity)
  def _minus[T: Basis[DAG, *]]: Prism[DAG[T], Minus[T]] =
    Prism.partial[DAG[T], Minus[T]] { case dag @ Minus(l, r) => dag }(identity)
  def _filter[T: Basis[DAG, *]]: Prism[DAG[T], Filter[T]] =
    Prism.partial[DAG[T], Filter[T]] {
      case dag @ Filter(funcs: NonEmptyList[Expression], expr) => dag
    }(identity)
  def _join[T: Basis[DAG, *]]: Prism[DAG[T], Join[T]] =
    Prism.partial[DAG[T], Join[T]] { case dag @ Join(l, r) => dag }(identity)
  def _offset[T: Basis[DAG, *]]: Prism[DAG[T], Offset[T]] =
    Prism.partial[DAG[T], Offset[T]] { case dag @ Offset(offset: Long, r) =>
      dag
    }(identity)
  def _limit[T: Basis[DAG, *]]: Prism[DAG[T], Limit[T]] =
    Prism.partial[DAG[T], Limit[T]] { case dag @ Limit(limit: Long, r) => dag }(
      identity
    )
  def _distinct[T: Basis[DAG, *]]: Prism[DAG[T], Distinct[T]] = Prism
    .partial[DAG[T], Distinct[T]] { case dag @ Distinct(r) => dag }(identity)
  def _reduced[T: Basis[DAG, *]]: Prism[DAG[T], Reduced[T]] = Prism
    .partial[DAG[T], Reduced[T]] { case dag @ Reduced(r) => dag }(identity)
  def _group[T: Basis[DAG, *]]: Prism[DAG[T], Group[T]] = Prism
    .partial[DAG[T], Group[T]] { case dag @ Group(_, _, _) => dag }(identity)
  def _order[T: Basis[DAG, *]]: Prism[DAG[T], Order[T]] = Prism
    .partial[DAG[T], Order[T]] { case dag @ Order(_, _) => dag }(identity)
  def _table[T: Basis[DAG, *]]: Prism[DAG[T], Table[T]] = Prism
    .partial[DAG[T], Table[T]] { case dag @ Table(_, _) => dag }(identity)
  def _exists[T: Basis[DAG, *]]: Prism[DAG[T], Exists[T]] = Prism
    .partial[DAG[T], Exists[T]] { case dag @ Exists(_, _, _) => dag }(identity)
  def _noop[T: Basis[DAG, *]]: Prism[DAG[T], Noop[T]] =
    Prism.partial[DAG[T], Noop[T]] { case dag @ Noop(trace: String) => dag }(
      identity
    )

  def _describeR[T: Basis[DAG, *]]: Prism[T, Describe[T]] =
    basisIso[DAG, T] composePrism _describe
  def _askR[T: Basis[DAG, *]]: Prism[T, Ask[T]] =
    basisIso[DAG, T] composePrism _ask
  def _constructR[T: Basis[DAG, *]]: Prism[T, Construct[T]] =
    basisIso[DAG, T] composePrism _construct
  def _scanR[T: Basis[DAG, *]]: Prism[T, Scan[T]] =
    basisIso[DAG, T] composePrism _scan
  def _projectR[T: Basis[DAG, *]]: Prism[T, Project[T]] =
    basisIso[DAG, T] composePrism _project
  def _bindR[T: Basis[DAG, *]]: Prism[T, Bind[T]] =
    basisIso[DAG, T] composePrism _bind
  def _sequenceR[T: Basis[DAG, *]]: Prism[T, Sequence[T]] =
    basisIso[DAG, T] composePrism _sequence
  def _pathR[T: Basis[DAG, *]]: Prism[T, Path[T]] =
    basisIso[DAG, T] composePrism _path
  def _bgpR[T: Basis[DAG, *]]: Prism[T, BGP[T]] =
    basisIso[DAG, T] composePrism _bgp
  def _leftjoinR[T: Basis[DAG, *]]: Prism[T, LeftJoin[T]] =
    basisIso[DAG, T] composePrism _leftjoin
  def _minusR[T: Basis[DAG, *]]: Prism[T, Minus[T]] =
    basisIso[DAG, T] composePrism _minus
  def _unionR[T: Basis[DAG, *]]: Prism[T, Union[T]] =
    basisIso[DAG, T] composePrism _union
  def _filterR[T: Basis[DAG, *]]: Prism[T, Filter[T]] =
    basisIso[DAG, T] composePrism _filter
  def _joinR[T: Basis[DAG, *]]: Prism[T, Join[T]] =
    basisIso[DAG, T] composePrism _join
  def _offsetR[T: Basis[DAG, *]]: Prism[T, Offset[T]] =
    basisIso[DAG, T] composePrism _offset
  def _limitR[T: Basis[DAG, *]]: Prism[T, Limit[T]] =
    basisIso[DAG, T] composePrism _limit
  def _distinctR[T: Basis[DAG, *]]: Prism[T, Distinct[T]] =
    basisIso[DAG, T] composePrism _distinct
  def _reducedR[T: Basis[DAG, *]]: Prism[T, Reduced[T]] =
    basisIso[DAG, T] composePrism _reduced
  def _groupR[T: Basis[DAG, *]]: Prism[T, Group[T]] =
    basisIso[DAG, T] composePrism _group
  def _orderR[T: Basis[DAG, *]]: Prism[T, Order[T]] =
    basisIso[DAG, T] composePrism _order
  def _tableR[T: Basis[DAG, *]]: Prism[T, Table[T]] =
    basisIso[DAG, T] composePrism _table
  def _existsR[T: Basis[DAG, *]]: Prism[T, Exists[T]] =
    basisIso[DAG, T] composePrism _exists
  def _noopR[T: Basis[DAG, *]]: Prism[T, Noop[T]] =
    basisIso[DAG, T] composePrism _noop

}
// scalastyle:on
