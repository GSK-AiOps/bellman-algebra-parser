import ExprParser.graphPattern
import fastparse._, MultiLineWhitespace._

object StringValParser {
  def string[_:P]:P[STRING] = P("\"" ~ CharsWhile(_ != '\"').! ~ "\"").map{STRING}
  //TODO improve regex. Include all valid sparql varnames in spec: https://www.w3.org/TR/sparql11-query/#rPN_CHARS_BASE
  def variable[_:P]:P[VARIABLE] = P("?" ~ CharsWhileIn("a-zA-Z0-9")).!.map{VARIABLE}
  def urival[_:P]:P[URIVAL] = P("<" ~ (CharsWhile(_ != '>')) ~ ">").!.map{URIVAL}

  def tripleValParser[_:P]:P[StringVal] = P(variable | urival | string)
}
