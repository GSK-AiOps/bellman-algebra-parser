package com.gsk.kg.sparqlparser

import cats.syntax.either._

import org.apache.jena.query.QueryFactory
import org.apache.jena.sparql.algebra.Algebra
import org.apache.jena.sparql.algebra.Op

import com.gsk.kg.sparqlparser.BuiltInFunc._
import com.gsk.kg.sparqlparser.Expr._
import com.gsk.kg.sparqlparser.Query.Construct
import com.gsk.kg.sparqlparser.Query.Describe
import com.gsk.kg.sparqlparser.Query.Select
import com.gsk.kg.sparqlparser.StringVal._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class QuerySamplesTestSpec
    extends AnyFlatSpec
    with Matchers
    with TestUtils
    with TestConfig {

  def showAlgebra(q: String): Op = {
    val query = QueryFactory.create(q)
    Algebra.compile(query)
  }

  "Get a small sample" should "parse" in {
    val query = QuerySamples.q1
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case OffsetLimit(None, Some(20), _) => succeed
        case _                              => fail
      }
      .getOrElse(fail)
  }

  "Find label" should "parse" in {
    val query = QuerySamples.q2
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, BGP(_)) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?label"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Find distinct label" should "parse" in {
    val query = QuerySamples.q3
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Distinct(Project(vs, BGP(_))) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?label"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get all relations" should "parse" in {
    val query = QuerySamples.q4
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, BGP(_)) =>
          assert(vs.nonEmpty && vs.size == 2)
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get parent class" should "parse" in {
    val query = QuerySamples.q5
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, BGP(_)) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?parent"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get parent class with filter" should "parse" in {
    val query = QuerySamples.q6
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, Filter(funcs, r)) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?parent"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test multiple hops" should "parse" in {
    val query = QuerySamples.q7
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, BGP(triples)) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?species"))
          assert(triples.size == 7)
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test multiple hops and prefixes" should "parse" in {
    val query = QuerySamples.q8
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, BGP(triples)) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?species"))
          assert(triples.size == 7)
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test find label" should "parse" in {
    val query = QuerySamples.q9
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, BGP(triples)) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?label"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test find parent class" should "parse" in {
    val query = QuerySamples.q10
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, BGP(triples)) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?parent"))
          assert(triples.size == 1)
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Tests hops and distinct" should "parse" in {
    val query = QuerySamples.q11
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Distinct(Project(vs, BGP(triples))) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?parent_name"))
          assert(triples.size == 2)
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Tests filter and bind" should "parse" in {
    val query = QuerySamples.q12
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, Filter(funcs, Extend(to, from, BGP(_)))) =>
          assert(vs.nonEmpty && vs.size == 3)
          assert(funcs.size == 1)
          assert(to == VARIABLE("?o"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  // ignore for now since for diff position, Jena generates different representations
  "Test BIND in another position in the query" should "parse to same as q12" in {
    val query = QuerySamples.q13
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(
              vs,
              Filter(funcs, Join(Extend(to, from, TabUnit()), BGP(_)))
            ) =>
          assert(vs.nonEmpty && vs.size == 3)
          assert(funcs.size == 1)
          assert(to == VARIABLE("?o"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test union" should "parse" in {
    val query = QuerySamples.q14
    val expr  = QueryConstruct.parseADT(query, config)
    expr
      .map {
        case Project(vs, Union(l, r)) =>
          assert(vs.nonEmpty && vs.size == 3)
          r match {
            case Filter(funcs, e) => succeed
            case _                => fail
          }
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test simple describe query" should "parse" in {
    val query = QuerySamples.q15
    parse(query, config).map {
      case (Describe(_, OpNil()), _) =>
        succeed
      case _ =>
        fail
    }
  }

  "Test describe query" should "parse" in {
    val query = QuerySamples.q16
    parse(query, config)
      .map {
        case (Describe(vars, Project(vs, Filter(_, _))), _) =>
          assert(vars == vs)
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Tests str conversion and logical operators" should "parse" in {
    val query = QuerySamples.q17
    parse(query, config)
      .map {
        case (Select(vars, Project(vs, Filter(_, _))), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Tests FILTER positioning with graph sub-patterns" should "parse" in {
    val query = QuerySamples.q18
    parse(query, config)
      .map {
        case (Select(vars, Project(vs, Filter(_, _))), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test for FILTER in different positions" should "parse" in {
    val query = QuerySamples.q19
    parse(query, config)
      .map {
        case (Select(vars, Distinct(Project(vs, Filter(_, _)))), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test CONSTRUCT and string replacement" should "parse" in {
    val query = QuerySamples.q20
    parse(query, config)
      .map {
        case (
              Construct(
                vars,
                _,
                Extend(to, REPLACE(_, _, _, _), r)
              ),
              _
            ) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Test document query" should "parse" in {
    val query = QuerySamples.q21
    parse(query, config)
      .map {
        case (Select(vars, Project(vs, BGP(ts))), _) =>
          assert(ts.size == 21)
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  //Comprehensive queries

  "Get a sample of triples joining non-blank nodes" should "parse" in {
    val query = QuerySamples.q22
    parse(query, config)
      .map {
        case (
              Select(
                vars,
                OffsetLimit(None, Some(10), Project(_, Filter(_, _)))
              ),
              _
            ) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Check DISTINCT works" should "parse" in {
    val query = QuerySamples.q23
    parse(query, config)
      .map {
        case (
              Select(
                vars,
                OffsetLimit(None, Some(10), Distinct(Project(vs, _)))
              ),
              _
            ) =>
          assert(vs.size == 3)
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get class parent-child relations" should "parse" in {
    val query = QuerySamples.q24
    parse(query, config)
      .map {
        case (Select(vars, Project(_, Filter(_, _))), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get class parent-child relations with optional labels" should "parse" in {
    val query = QuerySamples.q25
    parse(query, config)
      .map {
        case (Select(vars, Project(_, Filter(_, _))), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get all labels in file" should "parse" in {
    val query = QuerySamples.q26
    parse(query, config)
      .map {
        case (Select(vars, Distinct(Project(_, _))), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get label of owl:Thing" should "parse" in {
    val query = QuerySamples.q27
    parse(query, config)
      .map {
        case (Select(vars, Project(vs, _)), _) =>
          assert(vs.nonEmpty && vs.head == VARIABLE("?label"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get label of owl:Thing with prefix" should "parse" in {
    val query = QuerySamples.q28
    parse(query, config)
      .map {
        case (Select(vars, Project(_, _)), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get label of owl:Thing with explanatory comment" should "parse" in {
    val query = QuerySamples.q29
    parse(query, config)
      .map {
        case (Select(vars, Project(_, _)), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Get label of owl:Thing with regex to remove poor label if present" should "parse" in {
    val query = QuerySamples.q30
    parse(query, config)
      .map {
        case (Select(vars, Project(_, Filter(_, _))), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Construct a graph where everything which is a Thing is asserted to exist" should "parse" in {
    val query = QuerySamples.q31
    parse(query, config)
      .map {
        case (Construct(vars, bgp, BGP(_)), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Construct a graph where all the terms derived from a species have a new relation" should "parse" in {
    val query = QuerySamples.q32
    parse(query, config)
      .map {
        case (Construct(vars, bgp, BGP(_)), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Detect punned relations in an ontology" should "parse" in {
    val query = QuerySamples.q33
    parse(query, config)
      .map {
        case (Select(vars, Project(_, _)), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Construct a triple where the predicate is derived" should "parse" in {
    val query = QuerySamples.q34
    parse(query, config)
      .map {
        case (Construct(vars, bgp, Union(BGP(_), BGP(_))), _) =>
          succeed
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Query to convert schema of predications" should "parse" in {
    val query = QuerySamples.q35
    parse(query, config)
      .map {
        case (Construct(vars, bgp, Extend(to, from, e)), _) =>
          assert(to == VARIABLE("?pred"))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Query with default and named graphs to match on specific graph" should "parse" in {
    val query = QuerySamples.q36
    parse(query, config.copy(isDefaultGraphExclusive = true))
      .map {
        case (
              Select(
                _,
                Project(_, Graph(specifiedGraph, _))
              ),
              graphs
            ) =>
          graphs.default should (have size 2 and contain theSameElementsAs Seq(
            URIVAL("<http://example.org/dft.ttl>"),
            URIVAL("")
          ))
          graphs.named should (have size 2 and contain theSameElementsAs Seq(
            URIVAL("<http://example.org/alice>"),
            URIVAL("<http://example.org/bob>")
          ))
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

  "Query with default and named graphs to match on multiple graphs" should "parse" in {
    val query = QuerySamples.q37
    parse(query, config.copy(isDefaultGraphExclusive = true))
      .map {
        case (
              Select(
                vars,
                Project(_, Join(_, Graph(graphVariable, _)))
              ),
              graphs
            ) =>
          graphs.default should (have size 2 and contain theSameElementsAs Seq(
            URIVAL("<http://example.org/dft.ttl>"),
            URIVAL("")
          ))
          graphs.named should (have size 2 and contain theSameElementsAs Seq(
            URIVAL("<http://example.org/alice>"),
            URIVAL("<http://example.org/bob>")
          ))
          graphVariable shouldEqual vars(1)
        case _ =>
          fail
      }
      .getOrElse(fail)
  }

}
