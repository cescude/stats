import java.io.{BufferedReader, InputStreamReader}

import org.rogach.scallop.ScallopConf

import scala.util.matching.Regex

object Main {

  case class Key( tokens: Seq[String] )
  case class Line( tokens: Seq[Token] ) {
    // Each line has a particular identifying key
    // (e.g., the text with numbers removed)
    def key: Key = Key(tokens.collect({
      case WordToken(w) => w.trim
    }))

    // Takes the values from `rline` and merges them
    // into the current line.
    def merge(rline: Line): Line = {
      Line(tokens.zip(rline.tokens).map({
        case (ltoken, rtoken) =>
          ltoken.merge(rtoken)
      }))
    }

    def repr(opts: DisplayOpts): String = tokens.map(_.repr(opts)).mkString("")
  }

  sealed trait Token {
    def merge( t: Token ): Token   // combines "this" with the token "t"
    def repr( opts: DisplayOpts ): String // convert this token to a printable value
  }
  case class WordToken( w: String ) extends Token {
    override def merge(t: Token): Token = t match {
      case WordToken(_) => t
      case _ => ???
    }

    override def repr(opts: DisplayOpts): String = w
  }

  def color(s: BigDecimal): fansi.Str = color(fansi.Str(s.toString))
  def color(s: fansi.Str): fansi.Str = fansi.Color.Blue(s)

  case class NumToken(mn: BigDecimal, mx: BigDecimal, current: BigDecimal) extends Token {
    override def merge(t: Token): Token = t match {
      case NumToken(mn0, _, c0) if mn0 < mn => NumToken(mn0, mx, c0)
      case NumToken(_, mx0, c0) if mx0 > mx => NumToken(mn, mx0, c0)
      case NumToken(_, _, c0) => NumToken(mn, mx, c0)
      case _ => ???
    }
    override def repr(opts: DisplayOpts): String = opts match {
      case MinAndMax if mn == mx => color(mn).render
      case MinAndMax if mn == current => "[" + color(mn) + "…" + mx + "]"
      case MinAndMax if mx == current => "[" + mn + "…" + color(mx) + "]"
      case MinAndMax => "[" + mn + "…" + mx + "]"

      case JustMinimums => color(mn).render
      case JustMaximums => color(mx).render

      case FullStat if mn == mx => color(mn).render
      case FullStat if mn == current => "[" + color(mn) + "…" + mx + "]"
      case FullStat if mx == current => "[" + mn + "…" + color(mx) + "]"
      case FullStat => "[" + mn + "…" + color(current) + "…" + mx + "]"
    }
  }

  var seen: Map[Key,Line] = Map()

  // Be slow? Who cares, it's fast enough
  @scala.annotation.tailrec
  def tokenize(s: String, acc: Seq[Token] ): Seq[Token] = {
    if ( s.isEmpty ) acc
    else {
      val numbx = raw"[0-9]+\.[0-9]+|[0-9]+".r
      val wordx = raw"[^0-9]+".r

      lazy val num = numbx.findPrefixOf(s).map({ text =>
        val n = BigDecimal(text)
        (text, NumToken(n, n, n))
      })

      lazy val txt = wordx.findPrefixOf(s).map({ text =>
        (text, WordToken(text))
      })

      val Some((text, token)) = num.orElse(txt)
      tokenize(s.substring(text.length), acc :+ token)
    }
  }

  sealed trait DisplayOpts
  case object JustMaximums extends DisplayOpts
  case object JustMinimums extends DisplayOpts
  case object MinAndMax extends DisplayOpts
  case object FullStat extends DisplayOpts

  val stdin = {
    val reader = new BufferedReader( new InputStreamReader( System.in ) )
    Iterator.continually(reader.readLine).takeWhile(_ != null)
  }

  class Config(args: Seq[String]) extends ScallopConf(args) {
    val mx = opt[Boolean]("max", short='x')
    val mn = opt[Boolean]("min")
    val full = opt[Boolean]("full")
    val reset = opt[String]("reset", descr="Supply a regex that will reset counts if the line matches")
    verify()

    def displayOpts: DisplayOpts =
      if (full.isSupplied) FullStat
      else if (mx.isSupplied) JustMaximums
      else if ( mn.isSupplied) JustMinimums
      else MinAndMax
  }

  def main(args: Array[String]): Unit = {

    val conf = new Config(args)
    val opts = conf.displayOpts

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

      println(mergedLine.repr(opts))

      seen = seen + (key -> mergedLine)
    })
  }
}
