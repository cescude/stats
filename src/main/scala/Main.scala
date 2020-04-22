import java.io.{BufferedReader, InputStreamReader}

import org.rogach.scallop.ScallopConf

import scala.util.matching.Regex

object Main {

  sealed trait DisplayOpts
  case object Default extends DisplayOpts
  case object Verbose extends DisplayOpts

  class Config(args: Seq[String]) extends ScallopConf(args) {
    val avg = opt[Boolean]("avg", short='a', descr="Display averages instead of a min/max range")
    val verbose = opt[Boolean]("verbose", short='v')
    val reset = opt[String]("reset", descr="Supply a regex that will reset counts if the line matches")
    verify()

    def displayOpts: DisplayOpts = if (verbose.isSupplied) Verbose else Default
    def variant: Variant = if (avg.isSupplied) AvgVariant else RangeVariant
  }

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

  // What type of numbers should we be tracking?
  sealed trait Variant
  case object AvgVariant extends Variant
  case object RangeVariant extends Variant

  def mkNumToken(variant: Variant, n: BigDecimal): Token = variant match {
    case AvgVariant => AvgToken(n, 1)
    case RangeVariant => RangeToken(n, n, n)
  }

  case class RangeToken(mn: BigDecimal, mx: BigDecimal, current: BigDecimal) extends Token {
    override def merge(t: Token): Token = t match {
      case RangeToken(mn0, _, c0) if mn0 < mn => RangeToken(mn0, mx, c0)
      case RangeToken(_, mx0, c0) if mx0 > mx => RangeToken(mn, mx0, c0)
      case RangeToken(_, _, c0) => RangeToken(mn, mx, c0)
      case _ => ???
    }

    override def repr(opts: DisplayOpts): String = opts match {
      case Default if mn == mx => color(mn).render
      case Default if mn == current => "[" + color(mn) + "…" + mx + "]"
      case Default if mx == current => "[" + mn + "…" + color(mx) + "]"
      case Default => "[" + mn + "…" + mx + "]"

      case Verbose if mn == mx => color(mn).render
      case Verbose if mn == current => "[" + color(mn) + "…" + mx + "]"
      case Verbose if mx == current => "[" + mn + "…" + color(mx) + "]"
      case Verbose => "[" + mn + "…" + color(current) + "…" + mx + "]"
    }
  }

  case class AvgToken(sum: BigDecimal, count: BigDecimal) extends Token {
    override def merge(t: Token): Token = t match {
      case AvgToken(sum0, count0) =>
        /*
        Combining averages; just need to track sum & count:
        avg(ns..) = sum(ns)/len(ns)
        avg(ms..) = sum(ms)/len(ms)
        avg(ns.. ++ ms..) =
            sum(ns)+sum(ms) / len(ns)+len(ms)
         */
        AvgToken(sum + sum0, count + count0)
      case _ => ???
    }

    override def repr(opts: DisplayOpts): String = {
      val avg = (sum / count).setScale(
        sum.scale + 1,
        BigDecimal.RoundingMode.HALF_UP)

      opts match {
        case Verbose => s"[${color(avg)}, #$count]"
        case Default => s"[${color(avg)}]"
      }
    }
  }

  // Be slow? Who cares, it's fast enough
  @scala.annotation.tailrec
  def tokenize(v: Variant, s: String, acc: Seq[Token]): Seq[Token] = {
    if ( s.isEmpty ) acc
    else {
      val numbx = raw"[0-9]+\.[0-9]+|[0-9]+".r
      val wordx = raw"[^0-9]+".r

      lazy val num = numbx.findPrefixOf(s).map({ text =>
        val n = BigDecimal(text)
        (text, mkNumToken(v, n))
      })

      lazy val txt = wordx.findPrefixOf(s).map({ text =>
        (text, WordToken(text))
      })

      val Some((text, token)) = num.orElse(txt)
      tokenize(v, s.substring(text.length), acc :+ token)
    }
  }

  val stdin: Iterator[String] = {
    val reader = new BufferedReader( new InputStreamReader( System.in ) )
    Iterator.continually(reader.readLine).takeWhile(_ != null)
  }

  var seen: Map[Key,Line] = Map()

  def main(args: Array[String]): Unit = {

    val conf = new Config(args)
    val opts = conf.displayOpts
    val variant = conf.variant

    val resetRx = conf.reset.toOption.map(_.r)

    stdin.foreach({ rawLine =>

      resetRx.flatMap(_.findFirstIn(rawLine)).foreach({ _ =>
        // We've been provided a reset regexp, and it matches
        // the current raw line. Reset our statistics db!
        seen = Map()
      })

      val newLine = Line(tokenize(variant, rawLine, Seq.empty))

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
