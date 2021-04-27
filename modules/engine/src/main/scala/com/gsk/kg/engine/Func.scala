package com.gsk.kg.engine

import org.apache.spark.sql._
import org.apache.spark.sql.functions.{concat => cc, _}
import org.apache.spark.sql.types.StringType

object Func {

  /** Performs logical binary operation '==' over two columns
    * @param l
    * @param r
    * @return
    */
  def equals(l: Column, r: Column): Column =
    applyOperator(l, r)(_ === _)

  /** Peforms logical binary operation '>' over two columns
    * @param l
    * @param r
    * @return
    */
  def gt(l: Column, r: Column): Column =
    applyOperator(l, r)(_ > _)

  /** Performs logical binary operation '<' over two columns
    * @param l
    * @param r
    * @return
    */
  def lt(l: Column, r: Column): Column =
    applyOperator(l, r)(_ < _)

  /** Performs logical binary operation '<=' over two columns
    * @param l
    * @param r
    * @return
    */
  def gte(l: Column, r: Column): Column =
    applyOperator(l, r)(_ >= _)

  /** Performs logical binary operation '>=' over two columns
    * @param l
    * @param r
    * @return
    */
  def lte(l: Column, r: Column): Column =
    applyOperator(l, r)(_ <= _)

  /** Performs logical binary operation 'or' over two columns
    * @param l
    * @param r
    * @return
    */
  def or(l: Column, r: Column): Column =
    l || r

  /** Performs logical binary operation 'and' over two columns
    * @param r
    * @param l
    * @return
    */
  def and(l: Column, r: Column): Column =
    l && r

  /** Negates all rows of a column
    * @param s
    * @return
    */
  def negate(s: Column): Column =
    not(s)

  /** Returns a column with 'true' or 'false' rows indicating whether a column has blank nodes
    * @param col
    * @return
    */
  def isBlank(col: Column): Column =
    when(regexp_extract(col, "^_:.*$", 0) =!= "", true)
      .otherwise(false)

  /** Implementation of SparQL REPLACE (without flags) on Spark dataframes.
    *
    * =Examples=
    *
    * | Function call                              | Result                     |
    * |:-------------------------------------------|:---------------------------|
    * | replace("abracadabra", "bra", "*")         | "a*cada*"                  |
    * | replace("abracadabra", "a.*a", "*")        | "*"                        |
    * | replace("abracadabra", "a.*?a", "*")       | "*c*bra"                   |
    * | replace("abracadabra", "a", "")            | "brcdbr"                   |
    * | replace("abracadabra", "a(.)", "a$1$1")    | "abbraccaddabbra"          |
    * | replace("abracadabra", ".*?", "$1")        | error (zero length string) |
    * | replace("AAAA", "A+", "b")                 | "b"                        |
    * | replace("AAAA", "A+?", "b")                | "bbbb"                     |
    * | replace("darted", "^(.*?)d(.*)$", "$1c$2") | "carted"                   |
    *
    * @see https://www.w3.org/TR/sparql11-query/#func-replace
    * @see https://www.w3.org/TR/xpath-functions/#func-replace
    * @param col
    * @param pattern
    * @param by
    * @return
    */
  def replace(col: Column, pattern: String, by: String): Column =
    regexp_replace(col, pattern, by)

  /** Implementation of SparQL STRAFTER on Spark dataframes.
    *
    * =Examples=
    *
    * | Function call                  | Result            |
    * |:-------------------------------|:------------------|
    * | strafter("abc","b")            | "c"               |
    * | strafter("abc"@en,"ab")        | "c"@en            |
    * | strafter("abc"@en,"b"@cy)      | error             |
    * | strafter("abc"^^xsd:string,"") | "abc"^^xsd:string |
    * | strafter("abc","xyz")          | ""                |
    * | strafter("abc"@en, "z"@en)     | ""                |
    * | strafter("abc"@en, "z")        | ""                |
    * | strafter("abc"@en, ""@en)      | "abc"@en          |
    * | strafter("abc"@en, "")         | "abc"@en          |
    *
    * TODO (pepegar): Implement argument compatibility checks
    *
    * @see [[https://www.w3.org/TR/sparql11-query/#func-strafter]]
    * @param col
    * @param str
    * @return
    */
  def strafter(col: Column, str: String): Column =
    when(substring_index(col, str, -1) === col, lit(""))
      .otherwise(substring_index(col, str, -1))

  /** Implementation of SparQL STRSTARTS on Spark dataframes.
    *
    * =Examples=
    *
    * | Function call                                      | Result |
    * |:---------------------------------------------------|:-------|
    * | strstarts("foobar", "foo")                         | true   |
    * | strstarts("foobar"@en, "foo"@en)                   | true   |
    * | strstarts("foobar"^^xsd:string, "foo"^^xsd:string) | true   |
    * | strstarts("foobar"^^xsd:string, "foo")             | true   |
    * | strstarts("foobar", "foo"^^xsd:string)             | true   |
    * | strstarts("foobar"@en, "foo")                      | true   |
    * | strstarts("foobar"@en, "foo"^^xsd:string)          | true   |
    * | strstarts("bar", "foo"^^xsd:string)                | false  |
    * | strstarts("bar", "foo")                            | false  |
    * | strstarts("foobar"@fr, "foo"@en)                   | error  |
    * | strstarts("foobar", "foo"@en)                      | error  |
    * | strstarts("foobar"^^xsd:string, "foo"@en)          | error  |
    *
    * TODO (pepegar): Implement argument compatibility checks
    *
    * @see [[https://www.w3.org/TR/sparql11-query/#func-strstarts]]
    * @param col
    * @param str
    * @return
    */
  def strstarts(col: Column, str: String): Column =
    when(col.startsWith(str), lit(true))
      .otherwise(lit(false))

  /** The IRI function constructs an IRI by resolving the string
    * argument (see RFC 3986 and RFC 3987 or any later RFC that
    * superceeds RFC 3986 or RFC 3987). The IRI is resolved against
    * the base IRI of the query and must result in an absolute IRI.
    *
    * The URI function is a synonym for IRI.
    *
    * If the function is passed an IRI, it returns the IRI unchanged.
    *
    * Passing any RDF term other than a simple literal, xsd:string or
    * an IRI is an error.
    *
    * An implementation MAY normalize the IRI.
    *
    * =Examples=
    *
    * | Function call          | Result            |
    * |:-----------------------|:------------------|
    * | IRI("http://example/") | <http://example/> |
    * | IRI(<http://example/>) | <http://example/> |
    *
    * TODO(pepegar): We need to check if it's feasible to validate
    * that values in the columns are URI formatted.
    *
    * @param col
    * @return
    */
  def iri(col: Column): Column =
    col

  /** synonym for [[Func.iri]]
    *
    * @param col
    * @return
    */
  def uri(col: Column): Column = iri(col)

  /** Concatenate two [[Column]] into a new one
    *
    * @param a
    * @param b
    * @return
    */
  def concat(a: Column, b: Column): Column =
    cc(a, b)

  /** Concatenate a [[String]] with a [[Column]], generating a new [[Column]]
    *
    * @param a
    * @param b
    * @return
    */
  def concat(a: String, b: Column): Column =
    concat(lit(a), b)

  /** Concatenate a [[Column]] with a [[String]], generating a new [[Column]]
    *
    * @param a
    * @param b
    * @return
    */
  def concat(a: Column, b: String): Column =
    concat(a, lit(b))

  /** Sample is a set function which returns an arbitrary value from
    * the multiset passed to it.
    *
    * Implemented using [[org.apache.spark.sql.functions.first]].
    *
    * @param col
    * @return
    */
  def sample(col: Column): Column =
    first(col, true)

  def groupConcat(col: Column, separator: String): Column =
    ???

  /** This helper method tries to parse a datetime expressed as a RDF
    * datetime string `"0193-07-03T20:50:09.000+04:00"^^xsd:dateTime`
    * to a column with underlying type datetime.
    *
    * @param col
    * @return
    */
  def parseDateFromRDFDateTime(col: Column): Column =
    when(
      regexp_extract(col, ExtractDateTime, 1) =!= lit(""),
      to_timestamp(regexp_extract(col, ExtractDateTime, 1))
    ).otherwise(lit(null)) // scalastyle:off

  private def applyOperator(l: Column, r: Column)(
      operator: (Column, Column) => Column
  ): Column =
    when(
      regexp_extract(l.cast(StringType), ExtractDateTime, 1) =!= lit("") &&
        regexp_extract(r.cast(StringType), ExtractDateTime, 1) =!= lit(""),
      operator(
        parseDateFromRDFDateTime(l.cast(StringType)),
        parseDateFromRDFDateTime(r.cast(StringType))
      )
    ).otherwise(operator(l, r))

  val ExtractDateTime = """^"(.*)"\^\^(.*)dateTime(.*)$"""
}
