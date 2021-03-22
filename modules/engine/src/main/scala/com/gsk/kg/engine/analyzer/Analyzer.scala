package com.gsk.kg.engine
package analyzer

import cats.implicits._
import higherkindness.droste.Basis
import higherkindness.droste.syntax.all._
import cats.Foldable
import cats.data.ValidatedNec
import cats.data.Validated._
import org.apache.spark.sql.DataFrame
import optics._
import cats.Traverse
import com.gsk.kg.engine.data.ChunkedList
import com.gsk.kg.sparqlparser.StringVal.VARIABLE

import DAG._

object Analyzer {

  def rules[T: Basis[DAG, *]]: List[Rule[T]] =
    List(FindUnboundVariables[T])


  /**
    * Execute all rules in [[Analyzer.rules]] and accumulate errors
    * that they may throw.
    *
    * In case no errors are returned, the
    *
    * @return
    */
  def analyze[T: Basis[DAG, *]]: Phase[T, T] =
    Phase { t =>
      val x: ValidatedNec[String, String] = Foldable[List].fold(rules.map(_(t)))

      x match {
        case Invalid(e) => M.lift[Result, DataFrame, T](EngineError.AnalyzerError(e).asLeft)
        case Valid(e) => t.pure[M]
      }
    }

}
