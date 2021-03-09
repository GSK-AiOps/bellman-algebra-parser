package com.gsk.kg.engine

import com.gsk.kg.sparqlparser.StringVal.VARIABLE
import org.apache.spark.sql.functions._
import org.apache.spark.sql.DataFrame
import cats.kernel.Semigroup
import org.apache.spark.sql.SQLContext
import cats.kernel.Monoid
import cats.syntax.all._
import org.apache.spark.sql.Column


/**
  * A [[Multiset]], as expressed in SparQL terms.
  *
  * @see See [[https://www.w3.org/2001/sw/DataAccess/rq23/rq24-algebra.html]]
  * @param bindings the variables this multiset expose
  * @param dataframe the underlying data that the multiset contains
  */
final case class Multiset(
  bindings: Set[VARIABLE],
  dataframe: DataFrame
) {

  /**
    * Join two multisets following SparQL semantics.  If two multisets
    * share some bindings, it performs an _inner join_ between them.
    * If they don't share any common binding, it performs a cross join
    * instead.
    *
    * =Spec=
    *
    * Defn: Join
    * Let Ω1 and Ω2 be multisets of mappings. We define:
    * Join(Ω1, Ω2) = { merge(μ1, μ2) | μ1 in Ω1and μ2 in Ω2, and μ1 and μ2 are compatible }
    * card[Join(Ω1, Ω2)](μ) = sum over μ in (Ω1 set-union Ω2), card[Ω1](μ1)*card[Ω2](μ2)
    *
    * @param other
    * @return the join result of both multisets
    */
  def join(other: Multiset): Multiset =
    (this, other) match {
      case (a, b) if a.isEmpty => b
      case (a, b) if b.isEmpty => a
      case (Multiset(aBindings, aDF), Multiset(bBindings, bDF)) if aBindings.intersect(bBindings).isEmpty =>
        val df = aDF.as("a")
          .crossJoin(bDF.as("b"))
        Multiset(aBindings.union(bBindings), df)
      case (a, b) =>
        val common = a.bindings.intersect(b.bindings)
        val df = a.dataframe.as("a")
          .join(b.dataframe.as("b"), common.map(_.s).toSeq)
        Multiset(a.bindings.union(b.bindings), df)
    }

  /**
    * Return wether both the dataframe & bindings are empty
    *
    * @return
    */
  def isEmpty: Boolean = bindings.isEmpty && dataframe.isEmpty

  /**
    * Get a new multiset with only the projected [[vars]].
    *
    * @param vars
    * @return
    */
  def select(vars: VARIABLE*): Multiset =
    Multiset(
      bindings.intersect(vars.toSet),
      dataframe.select(vars.map(v => dataframe(v.s)): _*)
    )


  /**
    * Perform a union between [[this]] and [[other]], as described in
    * SparQL Algebra doc.
    *
    * =Spec=
    *
    * Defn: Union
    * Let Ω1 and Ω2 be multisets of mappings. We define:
    * Union(Ω1, Ω2) = { μ | μ in Ω1 or μ in Ω2 }
    * card[Union(Ω1, Ω2)](μ) = card[Ω1](μ) + card[Ω2](μ)
    *
    * @param other
    * @return the Union of both multisets
    */
  def union(other: Multiset): Multiset =
    (this, other) match {
      case (a, b) if a.isEmpty => b
      case (a, b) if b.isEmpty => a
      case (Multiset(aBindings, aDF), Multiset(bBindings, bDF)) if aDF.columns == bDF.columns =>
        Multiset(
          aBindings.union(bBindings),
          aDF.union(bDF)
        )
      case (Multiset(aBindings, aDF), Multiset(bBindings, bDF)) =>

        val colsA = aDF.columns.toSet
        val colsB = bDF.columns.toSet
        val union = colsA.union(colsB)

        def genColumns(current: Set[String], total: Set[String]) = {
          total.map(x => x match {
            case x if current.contains(x) => col(x)
            case _ => lit(null).as(x) // scalastyle:ignore
          }).toList
        }

        Multiset(
          aBindings.union(bBindings),
          aDF.select(genColumns(colsA, union):_*).unionAll(bDF.select(genColumns(colsB, union):_*))
        )
    }

  /**
    * Add a new column to the multiset, with the given binding
    *
    * @param binding
    * @param col
    * @param fn
    * @return
    */
  def withColumn(
    binding: VARIABLE,
    column: Column
  ): Multiset =
    Multiset(
      bindings + binding,
      dataframe
        .withColumn(binding.s, column)
    )

  /**
    * A limited solutions sequence has at most given, fixed number of members.
    * The limit solution sequence S = (S0, S1, ..., Sn) is
    * limit(S, m) =
    * (S0, S1, ..., Sm-1) if n > m
    * (S0, S1, ..., Sn) if n <= m-1
    * @param limit
    * @return
    */
  def limit(limit: Long): Result[Multiset] = limit match {
    case l if l < 0 =>
      EngineError.UnexpectedNegative("Negative limit: $l").asLeft
    case l if l > Int.MaxValue.toLong =>
      EngineError.NumericTypesDoNotMatch(s"$l to big to be converted to an Int").asLeft
    case l =>
      this.copy(dataframe = dataframe.limit(l.toInt)).asRight
  }

  /**
    * An offset solution sequence with respect to another solution sequence S, is one which starts at a given index of S.
    * For solution sequence S = (S0, S1, ..., Sn), the offset solution sequence
    * offset(S, k), k >= 0 is
    * (Sk, Sk+1, ..., Sn) if n >= k
    * (), the empty sequence, if k > n
    * @param offset
    * @return
    */
  def offset(offset: Long): Result[Multiset] = if (offset < 0) {
    EngineError.UnexpectedNegative(s"Negative offset: $offset").asLeft
  } else {
    val rdd = dataframe.rdd.zipWithIndex.filter { case (_, idx) => idx >= offset }.map(_._1)
    val df = dataframe.sqlContext.sparkSession.createDataFrame(rdd, dataframe.schema)
    this.copy(dataframe = df).asRight
  }

  /**
    * Filter restrict the set of solutions according to a given expression.
    * @param col
    * @return
    */
  def filter(col: Column): Result[Multiset] = {
    val filtered = dataframe.filter(col)
    this.copy(dataframe = filtered).asRight
  }

  /**
    * A left join returns all values from the left relation and the matched values from the right relation,
    * or appends NULL if there is no match. It is also referred to as a left outer join.
    * @param r
    * @return
    */
  def leftJoin(r: Multiset): Result[Multiset] = {
    val cols: Seq[String] = (this.bindings intersect r.bindings).toSeq.map(_.s)
    this.copy(
      bindings = this.bindings union r.bindings,
      dataframe = this.dataframe.join(r.dataframe, cols, "left")
    ).asRight
  }

}

object Multiset {

  implicit val semigroup: Semigroup[Multiset] = new Semigroup[Multiset] {
    def combine(x: Multiset, y: Multiset): Multiset = x.join(y)
  }

  implicit def monoid(implicit sc: SQLContext): Monoid[Multiset] = new Monoid[Multiset] {
    def combine(x: Multiset, y: Multiset): Multiset = x.join(y)
    def empty: Multiset = Multiset(Set.empty, sc.emptyDataFrame)
  }

}
