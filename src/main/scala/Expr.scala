sealed trait Expr

final case class BGP(triples:Seq[Triple]) extends Expr
final case class Triple(s:StringVal, p:StringVal, o:StringVal) extends Expr
final case class LeftJoin(l:Expr, r:Expr) extends Expr
final case class Union(l:Expr, r:Expr) extends Expr
final case class Extend(bindTo:StringVal, bindFrom:StringVal, r:Expr) extends Expr
//TODO extend filter function types from strings to case classes
final case class FilterFunction(func:String, left:String, right:String) extends Expr
final case class Filter(funcs:Seq[FilterFunction], expr:Expr) extends Expr
final case class Join(l:Expr, r:Expr) extends Expr
final case class Graph(g:StringVal, e:Expr) extends Expr
