package ml.wolfe.fg

import ml.wolfe.MoreArrayOps._
import ml.wolfe.{FactorieVector, FactorGraph}
import ml.wolfe.FactorGraph.{Node, Edge}
import scalaxy.loops._
import scala.util.Random
import cc.factorie.la.DenseTensor1
import math._


/**
 * @author Sebastian Riedel
 */
trait Var {

  def asDiscrete = this.asInstanceOf[DiscreteVar]
  def asVector = this.asInstanceOf[VectorVar]
  def setup()
  def initializeToNegInfinity()
  def initializeRandomly(eps:Double)
  def updateN2F(edge: FactorGraph.Edge)
  def updateMaxMarginalBelief(node: FactorGraph.Node)
  def updateMarginalBelief(node: FactorGraph.Node)
  def entropy():Double

}

final class DiscreteVar(var dim: Int) extends Var {
  /* node belief */
  var b = Array.ofDim[Double](dim)

  /* external message for this node. Will usually not be updated during inference */
  var in = Array.ofDim[Double](dim)

  /* the domain of values. By default this corresponds to [0,dim) but can be a subset if observations are given */
  var domain: Array[Int] = _

  /* indicates that variable is in a certain state */
  var setting: Int = 0

  /* indicates the value corresponding to the setting of the node */
  var value: Int = 0

  def entropy() = {
    var result = 0.0
    for (i <- (0 until b.length).optimized) {
      result -= math.exp(b(i)) * b(i) //messages are in log space
    }
    result
  }

  def initializeToNegInfinity() = {
    fill(b, Double.NegativeInfinity)
  }


  def initializeRandomly(eps:Double) = {
    for (i <- b.indices) b(i) = Random.nextGaussian() * eps
  }

  def setup() {
    if (domain == null || domain.length != dim) domain = Array.range(0, dim)
    if (b == null || b.length != dim) b = Array.ofDim[Double](dim)
    if (in == null || in.length != dim) in = Array.ofDim[Double](dim)
  }

  def updateN2F(edge: Edge) = {
    val node = edge.n
    val m = edge.msgs.asDiscrete
    System.arraycopy(in, 0, m.n2f, 0, m.n2f.length)
    for (i <- (0 until dim).optimized) {
      for (e <- (0 until node.edges.length).optimized; if e != edge.indexInNode)
        m.n2f(i) += node.edges(e).msgs.asDiscrete.f2n(i)
    }

  }
  def updateMaxMarginalBelief(node: Node) = {
    System.arraycopy(in, 0, b, 0, b.length)
    for (e <- 0 until node.edges.length) {
      for (i <- 0 until dim)
        b(i) += node.edges(e).msgs.asDiscrete.f2n(i)
      maxNormalize(b)
    }
  }
  def updateMarginalBelief(node: Node) = {
    System.arraycopy(in, 0, b, 0, b.length)
    for (e <- 0 until node.edges.length) {
      for (i <- 0 until dim)
        b(i) += node.edges(e).msgs.asDiscrete.f2n(i)
      val logZ = math.log(b.view.map(exp).sum)
      -=(b,logZ)
    }
  }


}

final class VectorVar(val dim:Int) extends Var {
  var b:FactorieVector = new DenseTensor1(dim)
  var setting:FactorieVector = null


  def entropy() = ???

  def setup() = {

  }


  def updateMarginalBelief(node: Node) = ???
  def updateN2F(edge: Edge) = {
    edge.msgs.asVector.n2f = b //todo: copy instead?
  }

  def initializeRandomly(eps:Double) = {
    for (i <- 0 until dim) b(i) = Random.nextGaussian() * eps
  }
  def initializeToNegInfinity() = {
    b := Double.NegativeInfinity
  }
  def updateMaxMarginalBelief(node: Node) = {

  }
}

