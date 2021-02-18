package com.gsk.kg.sparqlparser

sealed trait ParserError extends RuntimeException

object ParserError {
  case class UnExpectedType(description: String) extends ParserError
}
