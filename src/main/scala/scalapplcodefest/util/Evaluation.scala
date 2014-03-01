package scalapplcodefest.util

case class EvaluationSummary(evaluations:Map[Any,Evaluation]) {
  override def toString = {
    val keys = evaluations.keys.toSeq.sortBy(_.toString)
    val lines = for (key <- keys.view) yield
      f"""
        |------------
        |Key:         $key
        |${evaluations(key)}
      """.stripMargin
    lines.mkString("\n")

  }
}

case class Evaluation(tp: Int = 0, tn: Int = 0, fp: Int = 0, fn: Int = 0) {
  def totalGold = tp + fn
  def totalGuess = tp + fp
  def precision = tp.toDouble / totalGuess
  def recall = tp.toDouble / totalGold
  def f1 = 2.0 * precision * recall / (precision + recall)
  def +(that: Evaluation) = Evaluation(tp + that.tp, tn + that.tn, fp + that.fp, fn + that.fn)
  override def toString =
    f"""Total Gold:  $totalGold
      |Total Guess: $totalGuess
      |Precision:   $precision%f
      |Recall:      $recall%f
      |F1:          $f1%f
    """.stripMargin
}

object Evaluator {
  def evaluate[T](target: Seq[T], guess: Seq[T])(attribute: T => Any): Evaluation = {
    val evaluations = for ((t, g) <- target.view zip guess.view) yield evaluate(t, g)(attribute)
    val reduced = evaluations.foldLeft(Evaluation())(_ + _)
    reduced
  }

  def evaluate[T](target: T, guess: T)(attribute: T => Any): Evaluation =
    if (attribute(target) == attribute(guess)) Evaluation(tp = 1, tn = 1) else Evaluation(fp = 1, fn = 1)

}