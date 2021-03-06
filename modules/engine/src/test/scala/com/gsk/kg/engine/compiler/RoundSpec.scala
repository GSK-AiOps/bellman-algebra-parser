package com.gsk.kg.engine.compiler

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row

import com.gsk.kg.sparqlparser.TestConfig

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RoundSpec
    extends AnyWordSpec
    with Matchers
    with SparkSpec
    with TestConfig {

  import sqlContext.implicits._

  "perform round function correctly" when {
    "round a valid double numeric" in {

      val df: DataFrame = List(
        ("_:a", "<http://xmlns.com/foaf/0.1/num>", "1.65"),
        ("_:b", "<http://xmlns.com/foaf/0.1/num>", "1.71"),
        ("_:c", "<http://xmlns.com/foaf/0.1/num>", "1.4")
      ).toDF("s", "p", "o")

      val query =
        """
          |PREFIX foaf: <http://xmlns.com/foaf/0.1/>
          |
          |SELECT ?r
          |WHERE  {
          |   ?x foaf:num ?num .
          |   bind(round(?num) as ?r)
          |}
          |""".stripMargin

      val expected = List("2.0", "2.0", "1.0").map(Row(_))

      Evaluation.eval(df, None, query, expected)
    }

    "term is a simple number" in {
      val df = List(
        (
          "<http://uri.com/subject/#a1>",
          "<http://xmlns.com/foaf/0.1/num>",
          "10.4"
        )
      ).toDF("s", "p", "o")

      val query =
        """
          |PREFIX foaf: <http://xmlns.com/foaf/0.1/>
          |
          |SELECT round(?num)
          |WHERE  {
          |   ?x foaf:num ?num
          |}
          |""".stripMargin

      val expected = List(Row("10.0"))

      Evaluation.eval(df, None, query, expected)
    }
  }
}
