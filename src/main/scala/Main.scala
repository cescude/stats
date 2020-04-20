import java.io.{BufferedReader, InputStreamReader}

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

    def repr(opts: Opts): String = tokens.map(_.repr(opts)).mkString("")
  }

  sealed trait Token {
    def merge( t: Token ): Token   // combines "this" with the token "t"
    def repr( opts: Opts ): String // convert this token to a printable value
  }
  case class WordToken( w: String ) extends Token {
    override def merge(t: Token): Token = t match {
      case WordToken(_) => this
      case _ => ???
    }

    override def repr(opts: Opts): String = w
  }

  def color(s: BigInt): fansi.Str = color(fansi.Str(s.toString))
  def color(s: fansi.Str): fansi.Str = fansi.Color.Blue(s)

  case class NumToken(mn: BigInt, mx: BigInt, current: BigInt) extends Token {
    override def merge(t: Token): Token = t match {
      case NumToken(mn0, _, c0) if mn0 < mn => NumToken(mn0, mx, c0)
      case NumToken(_, mx0, c0) if mx0 > mx => NumToken(mn, mx0, c0)
      case n@NumToken(_, _, c0) => NumToken(mn, mx, c0)
      case _ => ???
    }
    override def repr(opts: Opts): String = opts match {
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
  def tokenize(opts: Opts, s: String, acc: Seq[Token] ): Seq[Token] = {
    if ( s.isEmpty ) acc
    else {
      val digits = "1234567890"
      val isNum = digits.contains(s(0))

      lazy val num = s.takeWhile(c => digits.contains(c))
      lazy val txt = s.takeWhile( c => !digits.contains(c) )

      val token = if ( isNum ) num else txt
      val packed = if ( isNum ) NumToken(BigInt(num), BigInt(num), BigInt(num)) else WordToken(txt)

      tokenize( opts, s.substring( token.length ), acc :+ packed )
    }
  }

  sealed trait Opts
  case object JustMaximums extends Opts
  case object JustMinimums extends Opts
  case object MinAndMax extends Opts
  case object FullStat extends Opts

  val stdin = {
    val reader = new BufferedReader( new InputStreamReader( System.in ) )
    Iterator.continually(reader.readLine).takeWhile(_ != null)
  }

  def main(args: Array[String]): Unit = {

    val opts = (args.contains("+mx"), args.contains("+mn"), args.contains("+full")) match {
      case (true, _, _) => JustMaximums
      case (_, true, _) => JustMinimums
      case (_, _, true) => FullStat
      case _ => MinAndMax
    }

    stdin
      .map(tokenize(opts, _, Seq.empty))
      .map(Line.apply)
      .foreach({ newLine =>

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
