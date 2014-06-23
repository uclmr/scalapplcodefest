package ml.wolfe

/**
 * @author Sebastian Riedel
 */
trait Operators {

  import scala.language.implicitConversions

  import Wolfe._

  def argmax[T, N: Ordering](dom:Iterable[T])(obj:T => N) = dom.maxBy(obj)
  def argmin[T, N: Ordering](dom:Iterable[T])(obj:T => N) = dom.minBy(obj)
  def sum[T, N: Numeric](dom:Iterable[T])(obj:T => N) = dom.map(obj).sum
  def logZ[T](dom:Iterable[T])(obj:T => Double) = math.log((dom map (x => math.exp(obj(x)))).sum)
  def max[T, N: Ordering](dom:Iterable[T])(obj:T => N) = dom.map(obj).max
  def map[A,B](dom:Iterable[A])(mapper:A => B):Iterable[B] = dom.map(mapper)

  def expect[T](dom:Iterable[T])(logLinear:T=>Double)(stats:T=>Vector) = {
    val lZ = logZ(dom)(logLinear)
    sum(dom){x => stats(x) * math.exp(logLinear(x) - lZ) }
  }


}

object BruteForceOperators extends Operators