package com.gsk.kg.engine.compiler

import com.gsk.kg.engine.Compiler
import com.gsk.kg.sparqlparser.TestConfig
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
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

      val result = Compiler.compile(df, query, config)

      val dfR: DataFrame = result match {
        case Left(e)  => throw new Exception(e.toString)
        case Right(r) => r
      }
      val expected = List(2, 2, 1).map(Row(_))
      dfR.show(false)
      dfR
        .collect()
        .toSet shouldEqual expected
    }
  }
}
