package ml.wolfe.term

import ml.wolfe.term

/**
 * @author riedel
 */
class Tuple2Dom[D1 <: Dom, D2 <: Dom](val dom1: D1, val dom2: D2) extends ProductDom {
  dom =>
  type Value = (dom1.Value, dom2.Value)
  type Var = DomVar
  type Term = DomTerm
  type Marginals = (dom1.Marginals, dom2.Marginals)

  val lengths = dom1.lengths + dom2.lengths

  override val dimensions = dom1.dimensions + dom2.dimensions

  def toValue(setting: Setting, offsets: Offsets = Offsets()) = {
    val arg1 = dom1.toValue(setting, offsets)
    val arg2 = dom2.toValue(setting, offsets + dom1.lengths)
    (arg1, arg2)
  }


  //trait Test extends Term
  def own(term: TypedTerm[Value]) = ???

  def toMarginals(msg: Msg, offsets: Offsets) = {
    val arg1 = dom1.toMarginals(msg, offsets)
    val arg2 = dom2.toMarginals(msg, offsets + dom1.lengths)
    (arg1, arg2)
  }

  def copyValue(value: Value, setting: Setting, offsets: Offsets = Offsets()): Unit = {
    dom1.copyValue(value._1, setting, offsets)
    dom2.copyValue(value._2, setting, offsets + dom1.lengths)
  }

  def copyMarginals(marginals: Marginals, msgs: Msg, offsets: Offsets = Offsets()): Unit = {
    dom1.copyMarginals(marginals._1, msgs, offsets)
    dom2.copyMarginals(marginals._2, msgs, offsets + dom1.lengths)
  }

  def fillZeroMsg(target: Msg, offsets: Offsets) = {
    dom1.fillZeroMsg(target, offsets)
    dom2.fillZeroMsg(target, offsets + dom1.lengths)
  }

  def variable(name: String): DomVar =
    StaticTuple2Var(name)

  def one = (dom1.one, dom2.one)

  def zero = (dom1.zero, dom2.zero)

  def Const(value: Value): DomTerm = new Tuple2DomTermImpl {
    val _1 = domain.dom1.Const(value._1)
    val _2 = domain.dom2.Const(value._2)
  }

  trait DomTerm extends super.DomTerm {
    def _1: domain.dom1.Term

    def _2: domain.dom2.Term
  }

  trait Tuple2DomTermImpl extends DomTerm with super.DomTermImpl {

    type ArgumentType = term.Term[Dom]

    def arguments = IndexedSeq(_1, _2)

    def copy(args: IndexedSeq[ArgumentType]) = new Tuple2DomTermImpl {
      val _1 = args(0).asInstanceOf[dom1.Term]
      val _2 = args(1).asInstanceOf[dom2.Term]
    }
  }

  trait DomVar extends super.DomVar with DomTerm {

    def var1: domain.dom1.Var

    def var2: domain.dom2.Var

    def _1 = var1

    def _2 = var2

  }

  case class StaticTuple2Var(name: String) extends DomVar {
    val var1 = domain.dom1.variable(name + "._1")
    val var2 = domain.dom2.variable(name + "._2")
  }

}

trait ProductDom extends Dom {
  dom =>

  def productName = dom.getClass.getSimpleName

  class Field[D <: Dom](val product: TypedTerm[Value], val domain: D, val start: Offsets)(fieldName:String = start.toString) extends Composed[D] {

    type ArgumentType = TypedTerm[Value]
    val arguments = IndexedSeq(product)

    def copy(args: IndexedSeq[ArgumentType]) = new Field(args(0),domain,start)(fieldName)

    override def composer(args: Settings) = new Composer(args) {

      //input(0).recordChangedOffsets = true

      def eval()(implicit execution: Execution) = {
        input(0).copyTo(output,start,Offsets.zero,domain.lengths)
      }
    }

    override def differentiatorImpl(wrt: Seq[term.Var[Dom]])(in: Settings, err: Setting, gradientAcc: Settings) =
      new ComposedDifferentiator(wrt,in,err,gradientAcc) {
        def localBackProp()(implicit execution: Execution) = {
          //todo: SR: why can't argErrors be reset to zero in the ComposedDiff code?
          argErrors(0).resetToZero()
          error.copyTo(argErrors(0),Offsets.zero,start,domain.lengths)
        }
      }

    def composerOld() = ???

    def differentiatorOld(wrt: Seq[term.Var[Dom]]) = ???

    override def toString = s"$product.$fieldName"
  }

  trait DomTermImpl extends super.DomTerm with Composed[dom.type] {


    override def toString = s"""$productName(${arguments.mkString(",")})"""

    override def composer(args: Settings) = new Composer(args) {
      val lengths = arguments.map(_.domain.lengths).toArray

      def eval()(implicit execution: Execution) = {
        var off = Offsets()
        for (i <- 0 until input.length) {
          input(i).copyTo(output, off, 1)
          off += lengths(i)
        }
        //append inputs to output

      }
    }

    def differentiatorOld(wrt: Seq[term.Var[Dom]]) = ???

  }

}