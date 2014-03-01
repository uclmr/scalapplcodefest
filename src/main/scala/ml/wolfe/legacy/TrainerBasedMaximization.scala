package ml.wolfe.legacy

import cc.factorie.optimize.{Trainer, Perceptron, OnlineTrainer, Example}
import cc.factorie.util.DoubleAccumulator
import cc.factorie.la.WeightsMapAccumulator
import cc.factorie.WeightsSet
import TermDSL._
import ml.wolfe.legacy.term._
import ml.wolfe._


/**
 * A minimizer of a differentiable real valued function. Uses factorie optimization wolfe and
 * the [[ml.wolfe.legacy.term.Differentiable]] pattern.
 */
object TrainerBasedMaximization {

  def minimize(weightVar:Variable[FactorieVector], objective: Term[Double], trainerFor: WeightsSet => Trainer = new OnlineTrainer(_, new Perceptron, 5)) = {
    val weightsSet = new WeightsSet
    val key = weightsSet.newWeights(new DenseVector(10000))
    val instances = TermConverter.asSeq(objective, doubles.add)
    val examples = for (instance <- instances) yield {
      Differentiator.differentiate(weightVar, TermConverter.pushDownConditions(instance)) match {
        case Some(gradientTerm) =>
          new Example {
            def accumulateValueAndGradient(value: DoubleAccumulator, gradient: WeightsMapAccumulator) = {
              val weights = weightsSet(key).asInstanceOf[FactorieVector]
              val state = State(Map(weightVar -> weights))
              val v = instance.value(state)
              val g = gradientTerm.value(state)
              value.accumulate(v)
              gradient.accumulate(key, g, -1.0)
            }
          }
        case _ => sys.error("Can't differentiate " + instance)
      }
    }
    val trainer = trainerFor(weightsSet)
    trainer.trainFromExamples(examples)
    weightsSet(key).asInstanceOf[FactorieVector]
  }
}

trait ContinuousArgminHint extends CompilerHint {
  def minimize(param: Variable[FactorieVector], objective: Term[Double]): FactorieVector
}

case class GradientBasedArgminHint(trainerFor: WeightsSet => Trainer = new OnlineTrainer(_, new Perceptron, 5))
  extends ContinuousArgminHint {

  def minimize(param: Variable[FactorieVector], objective: Term[Double]) = {
    val weightsSet = new WeightsSet
    val key = weightsSet.newWeights(new DenseVector(10000))
    val instances = TermConverter.asSeq(objective, doubles.add)
    val examples = for (instance <- instances) yield {
      TermConverter.pushDownConditions(instance) match {
        case Differentiable(p, gradientTerm) if p == param =>
          new Example {
            def accumulateValueAndGradient(value: DoubleAccumulator, gradient: WeightsMapAccumulator) = {
              val weights = weightsSet(key).asInstanceOf[FactorieVector]
              val state = State(Map(param -> weights))
              val v = instance.value(state)
              val g = gradientTerm.value(state)
              value.accumulate(v)
              gradient.accumulate(key, g, -1.0)
            }
          }
        case _ => sys.error("Can't differentiate " + instance)
      }
    }
    val trainer = trainerFor(weightsSet)
    trainer.trainFromExamples(examples)
    weightsSet(key).asInstanceOf[FactorieVector]
  }
}