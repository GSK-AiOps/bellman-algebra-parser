package com.gsk.kg.sparqlparser
import com.gsk.kg.sparqlparser.StringVal.VARIABLE

import higherkindness.droste.macros.deriveFixedPoint

@deriveFixedPoint sealed trait Expr

object Expr {
  final case class BGP(triples:Seq[Triple]) extends Expr
  final case class Triple(s:StringVal, p:StringVal, o:StringVal) extends Expr {
    def getPredicates: List[(StringVal, String)] = {
      List((s, "s"),(p, "p"),(o, "o")).filterNot(_._1.isVariable)
    }
    def getVariables: List[(StringVal, String)] = {
      List((s, "s"),(p, "p"),(o, "o")).filter(_._1.isVariable)
    }
  }
  final case class LeftJoin(l:Expr, r:Expr) extends Expr
  final case class FilteredLeftJoin(l:Expr, r:Expr, f:FilterFunction) extends Expr
  final case class Union(l:Expr, r:Expr) extends Expr
  final case class Extend(bindTo:StringLike, bindFrom:StringLike, r:Expr) extends Expr
  final case class Filter(funcs:Seq[FilterFunction], expr:Expr) extends Expr
  final case class Join(l:Expr, r:Expr) extends Expr
  final case class Graph(g:StringVal, e:Expr) extends Expr
  final case class Construct(vars: Seq[VARIABLE], bgp: BGP, r:Expr) extends Expr
  final case class Select(vars: Seq[VARIABLE], r:Expr) extends Expr
}
