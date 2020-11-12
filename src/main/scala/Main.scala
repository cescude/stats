import java.io.{BufferedReader, InputStreamReader}

import org.rogach.scallop.ScallopConf

import scala.util.matching.Regex

object Main {

  class Config(args: Seq[String]) extends ScallopConf(args) {
    val avg = opt[Boolean]("avg", short='a', descr="Include average value in range")
    val count = opt[Boolean]("count", short='c', descr="Include sample count")
    val reset = opt[String]("reset", descr="Supply a regex that will reset counts if the line matches")
    val highlight = opt[Boolean]("highlight")
    val leaderboard = opt[Boolean]("leaderboard", short='l', "Show leaderboard instead of line-by-line output")
    verify()
  }

  case class Key( tokens: Seq[String] ) extends Ordered[Key] {
    val sortKey = tokens.mkString(" ")
    override def compare(that: Key): Int = sortKey compareTo that.sortKey
  }
  case class Line( tokens: Seq[Token], count: BigInt = 1 ) {
    // Each line has a particular identifying key
    // (e.g., the text with numbers removed)
    def key: Key = Key(tokens.collect({
      case WordToken(w) => w.trim
    }))

    // Takes the values from `rline` and merges them
    // into the current line.
    def merge(rline: Line): Line = {
      Line(
        tokens.zip(rline.tokens).map({
          case (ltoken, rtoken) =>
            ltoken.merge(rtoken)
        }),
        count + rline.count
      )
    }

    def repr(conf: Config): String = {
      val prefix = if (conf.count.isSupplied) s"#=$count" else ""
      val tokenRepr = tokens.map(_.repr(conf)).mkString("")
      s"$prefix $tokenRepr"
    }
  }

  sealed trait Token {
    def merge( t: Token ): Token   // combines "this" with the token "t"
    def repr( conf: Config ): String // convert this token to a printable value
  }

  case class WordToken( w: String ) extends Token {
    override def merge(t: Token): Token = t match {
      case WordToken(_) => t
      case _ => ???
    }

    override def repr(conf: Config): String = w
  }

  def color(s: BigDecimal): fansi.Str = color(fansi.Str(s.toString))
  def color(s: fansi.Str): fansi.Str = fansi.Color.Blue(s) //fansi.Color.Blue(s)

  object NumToken {
    def apply(n: BigDecimal): NumToken = NumToken(n, n, n, n, 1)
  }

  case class NumToken(mn: BigDecimal,
                      mx: BigDecimal,
                      current: BigDecimal,
                      sum: BigDecimal,
                      count: BigDecimal) extends Token {
    override def merge(t: Token): Token = {

      // Just die if we've not been given a NumToken
      val NumToken(mn0, mx0, current0, sum0, count0) = t

      NumToken(
        if (mn0 < mn) mn0 else mn,
        if (mx0 > mx) mx0 else mx,
        current0,
        sum + sum0,
        count + count0)
    }

    override def repr(conf: Config): String = {

      // Round the average to at most one more decimal place than what's
      // available for the sum...

      lazy val avg = (sum / count).setScale(
        sum.scale + 1,
        BigDecimal.RoundingMode.HALF_UP)

      Seq( (mn == mx) -> s"$current",
           (mn != mx) -> s"$mn…$current…$mx",
           (conf.avg.isSupplied && mn != mx) -> s"μ=$avg")
        .filter( _._1 )
        .map( _._2 )
        .map( s => if (conf.highlight.isSupplied) color(s) else s )
        .mkString("[", ",", "]")
    }
  }

  val numbx = raw"[0-9]+\.[0-9]+|[0-9]+".r
  val wordx = raw"[^0-9]+".r

  // Be slow? Who cares, it's fast enough
  @scala.annotation.tailrec
  def tokenize(s: String, acc: Seq[Token]): Seq[Token] = {
    if ( s.isEmpty ) acc
    else {
      lazy val num = numbx.findPrefixOf(s).map({ text =>
        val n = BigDecimal(text)
        (text, NumToken(n))
      })

      lazy val txt = wordx.findPrefixOf(s).map({ text =>
        (text, WordToken(text))
      })

      val Some((text, token)) = num.orElse(txt)
      tokenize(s.substring(text.length), acc :+ token)
    }
  }

  val stdin: Iterator[String] = {
    val reader = new BufferedReader( new InputStreamReader( System.in ) )
    Iterator.continually(reader.readLine).takeWhile(_ != null)
  }

  def main(args: Array[String]): Unit = {

    var seen: Map[Key,Line] = Map()
    var leaderboard: Seq[Key] = Seq()
    var lastWrite: Long = System.currentTimeMillis()

    val conf = new Config(args)
    val resetRx = conf.reset.toOption.map(_.r)

    stdin.foreach({ rawLine =>

      resetRx.flatMap(_.findFirstIn(rawLine)).foreach({ _ =>
        // We've been provided a reset regexp, and it matches
        // the current raw line. Reset our statistics db!
        seen = Map()
      })

      val newLine = Line(tokenize(rawLine, Seq.empty))

      // Key is just a list of words, no numbers included
      val key = newLine.key

      val mergedLine = seen.get(key) match {

        // We've already seen a line that looks similar to this.
        // Merge new data into the existing data
        case Some(line) => line.merge(newLine)

        // This list of tokens represents a new, never-before-seen
        // line, so just copy it over as-is
        case None => newLine
      }

      seen = seen + (key -> mergedLine)

      if (conf.leaderboard.isSupplied) {

        leaderboard = key +: leaderboard
          .filter(_ != key)
          .take(10)

        val now = System.currentTimeMillis()

        if (now - lastWrite > 100) {
          printLeaderboard(conf, leaderboard, seen, true)
          lastWrite = now
        }
      }
      else {
        println(mergedLine.repr(conf))
      }
    })

    if (conf.leaderboard.isSupplied) {

      // Print all seen patterns, not just the most recent 10
      printLeaderboard(conf, seen.keys.toSeq, seen, false)
    }
  }

  def printLeaderboard(conf: Config, leaderboard: Seq[Key], seen: Map[Key, Line], reset: Boolean) = {

    // Print the entries associated with the keys in our leaderboard
    leaderboard.sorted
      .flatMap(seen.get)
      .map(_.repr(conf))
      .foreach { line =>
        // Print the line, and clear any junk after it
        println(s"${line}\u001b[0K")
      }

    if ( reset ) {
      // Move all the way to the left and return to start
      if (leaderboard.nonEmpty) {
        print(s"\r\u001b[${leaderboard.size}A")
      }
    }
  }
}
