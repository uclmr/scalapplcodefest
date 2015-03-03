package ml.wolfe.term

import cc.factorie.Factorie.DenseTensor1
import cc.factorie.la.{SingletonTensor1, DenseTensor2}
import ml.wolfe.term.TermImplicits.RichToLog
import ml.wolfe.{FactorieMatrix, FactorieVector}
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.macros.blackbox
import scala.util.Random

/**
 * @author riedel
 */
object TermImplicits extends NameProviderImplicits with MathImplicits with Stochastic with LoggedTerms {

  implicit val Doubles: Dom.doubles.type = Dom.doubles
  implicit val Bools: Dom.bools.type = Dom.bools
  implicit val Ints: Dom.ints.type = Dom.ints

  def Discretes[T](args: T*) = new DiscreteDom[T](args.toIndexedSeq)
  
  def Ints[T](args: Range) = new RangeDom(args)

  implicit def rangeToDom(range: Range): RangeDom = new RangeDom(range)

  implicit def domToIterable(dom: Dom): Iterable[dom.Value] = dom.toIterable

  def seqs[D <: Dom](elements: D, length: Int): SeqDom[elements.type] = new SeqDom[elements.type](elements, length)

  def Seqs[D <: Dom](elements: D, minLength: Int, maxLength: Int): VarSeqDom[elements.type] =
    new VarSeqDom[elements.type](elements, maxLength, minLength)

  def Seqs[D <: Dom](elements: D, length: Int): VarSeqDom[elements.type] = Seqs(elements, length, length)

  def fixedLengthSeq[T](elements: Seq[T])(implicit dom: TypedDom[T]) = {
    Seqs(dom, elements.length).Const(elements.toIndexedSeq)
  }

  def SeqConst[T](elements: T*)(implicit dom: TypedDom[T]) = {
    fixedLengthSeq[T](elements)
  }


  def mem[T <: Term[Dom]](term:T) = term.domain.own(new Memoized[Dom,T](term).asInstanceOf[TypedTerm[term.domain.Value]])

  def choice[T <: Term[Dom]](index:IntTerm)(choices:T*):T = SeqTerm(choices:_*)(index).asInstanceOf[T]

  def ifThenElse[T <: Term[Dom]](cond:BoolTerm)(ifTrue:T)(ifFalse:T) = choice(boolToInt(cond))(ifTrue,ifFalse)

  implicit class ConvertableToTerm[T, D <: TypedDom[T]](value: T)(implicit val domain: D) {
    def toConst: domain.Term = domain.Const(value)
  }

  implicit class RichRange(values: Range) {
    def toDom = new RangeDom(values)
  }

  implicit class RichSeq[T](values: Seq[T]) {
    def toDom = new DiscreteDom[T](values.toIndexedSeq)
    def toConst(implicit dom:TypedDom[T]) = fixedLengthSeq[T](values)
  }

  def seq[E <: Dom](dom: SeqDom[E])(elems: dom.elementDom.Term*): dom.SeqDomTermImpl = new dom.SeqDomTermImpl {
    def elements = elems.toIndexedSeq

    def copy(args: IndexedSeq[ArgumentType]) = seq(dom)(args: _*)
  }


  //  implicit def genericToConstant[T,D<:TypedDom[T]](t:T)(implicit dom:D):dom.Term = dom.const(t)
  //  implicit def genericToConstant[T,D<:TypedDom[T]](t:T)(implicit dom:D):dom.DomTerm = dom.const(t)

  //  implicit def seqOfTermsToSeqTerm[T <: Term[Dom], D <: DomWithTerm[T],S<:SeqDom[D]](seq:IndexedSeq[T])(implicit dom:S):dom.Term =
  //    new dom.SeqTermImpl {
  //      val elements:IndexedSeq[T] = seq
  //      val domain:dom.type = dom
  //    }

  def SeqTerm[D <: Dom](args:Term[D]*):VarSeqDom[D]#Term  = VarSeq[D](Ints.Const(args.length),args.toIndexedSeq)
  def SeqTerm[D <: Dom](length:IntTerm)(args:Term[D]*):VarSeqDom[D]#Term  = VarSeq[D](length,args.toIndexedSeq)

  def VarSeq[D <: Dom](length: TypedTerm[Int], args: IndexedSeq[Term[D]]): VarSeqDom[D]#Term = {
    val dom = Seqs[D](args.head.domain, args.length)
    dom.Term(length, args.asInstanceOf[IndexedSeq[dom.elementDom.Term]])
    //    new VarSeqConstructor[D](length, args)
  }

  def stochastic[T](gen: Dynamic[T])(arg: Dynamic[T] => DoubleTerm) = {
    val term = arg(gen)
    new DynamicTerm[T] {
      def generator: Dynamic[T] = gen

      def self: DoubleTerm = term
      def copy(args: IndexedSeq[ArgumentType]) = ???

    }
  }


  //implicit def seqToConstant[T,D<:TypedDom[T]](seq:IndexedSeq[T])(implicit dom:SeqDom[D]):dom.TermType = dom.const(seq)

  //implicit def seqToSeqTerm[E <: Dom : SeqDom](elems:Seq[Term[E]]) = seq(implicitly[SeqDom[E]])(elems: _*)


  implicit def discToConstant[T: DiscreteDom](value: T): Constant[DiscreteDom[T]] =
    implicitly[DiscreteDom[T]].Const(value)


  //  def argmax[D <: Dom](dom: D)(obj: dom.Variable => DoubleTerm): dom.Value = {
  //    val variable = dom.variable("_hidden")
  //    val term = obj(variable)
  //    term.argmax(variable).asInstanceOf[dom.Value]
  //  }


  implicit class RichBoolTerm(term: BoolTerm) {
    def &&(that: BoolTerm) = new And(term, that)

    def ||(that: BoolTerm) = new Or(term, that)

    def -->(that: BoolTerm) = new Implies(term, that)

    def unary_! = for (b <- term) yield !b

  }

  implicit class RichDiscreteTerm[T](term: DiscreteTerm[T]) {
    def ===(that: DiscreteTerm[T]) = new DiscreteEquals(term, that)
  }

  implicit class RichTermToType[D <: Dom](val term: Term[D]) {
    def typed[A <: Dom](dom: A): dom.Value => term.domain.Value = {
      require(term.vars.size == 1 && term.vars.head.domain == dom)
      (a: dom.Value) => term.eval(Seq(a): _*)
    }
  }

  implicit class RichToLog[T <: Term[Dom]](val term:T) {
    def logged(name:String) =
      term.domain.own(new LogTerm[Dom,Term[Dom]](term,name).asInstanceOf[TypedTerm[term.domain.Value]])

    def logged(implicit name:NameProvider) =
      term.domain.own(new LogTerm[Dom,Term[Dom]](term,name.newName()).asInstanceOf[TypedTerm[term.domain.Value]])

  }

  //  implicit class RichSeqTerm[E <: Dom, S <: Term[SeqDom[E]]](val term:S) {
  //    def apply[I <: Term[TypedDom[Int]]](index:I) =
  //      new SeqApply[E,S,I](term,index)
  //  }

  implicit class RichMonadTerm[A <: Term[Dom]](val termToBeMapped: A) {
    def map[B](fun: termToBeMapped.domain.Value => B)(implicit targetDom: TypedDom[B]): targetDom.Term = {
      val result = new TermMap[A, targetDom.type] {
        val term: termToBeMapped.type = termToBeMapped
        val domain: targetDom.type = targetDom

        def f(arg: term.domain.Value) = fun(arg)
      }
      targetDom.own(result)
    }

    def flatMap[B: TypedDom](fun: termToBeMapped.domain.Value => Term[TypedDom[B]]) = {
      val targetDom = implicitly[TypedDom[B]]
      new TermFlatMap[A, TypedDom[B]] {
        val term: termToBeMapped.type = termToBeMapped
        val domain: TypedDom[B] = targetDom

        def f(arg: term.domain.Value): Term[TypedDom[B]] = fun(arg)
      }
    }
  }


  implicit class RichDom[D <: Dom](val dom: D) {
    def x[D2 <: Dom](that: D2): Tuple2Dom[dom.type, that.type] = new Tuple2Dom[dom.type, that.type](dom, that)

    //    def iterator = dom.iterator
  }


  ////  implicit class RichVarSeqTerm[E <: Dom, T <: Term[VarSeqDom[E]]](val term: T) {
  ////    def apply(index: Int) =
  ////      new VarSeqApply[E, T, term.domain.lengthDom.Term](term, term.domain.lengthDom.const(index))
  ////
  ////    def length = new VarSeqLength[T](term)
  ////
  ////    //def apply(index:Int) = term.elements(index)
  ////
  ////    def sum(body: term.domain.elementDom.Var => DoubleTerm) = {
  ////      new FirstOrderSum[E,DoubleTerm,T](term,???,???)
  ////    }
  ////
  ////  }
  //  implicit class RichVarSeqTerm[T <: Term[VarSeqDom[Dom]]](val term:T) {
  //    type NewE = term.domain.elementDom.type
  //    type NewT = Term[VarSeqDom[term.domain.elementDom.type]]
  //    type ResultType = Term[term.domain.elementDom.type]
  //    type ResultTypeOld = VarSeqApply[NewE,NewT,TypedTerm[Int]]
  //
  //  def apply(index:TypedTerm[Int]):ResultType =
  //      new VarSeqApply[Dom,Term[VarSeqDom[Dom]],TypedTerm[Int]](term,index).asInstanceOf[ResultType]
  //  }


}

trait MathImplicits {

  def Vectors(dim: Int) = new VectorDom(dim)

  def UnitVectors(dim: Int) = new UnitVectorDom(dim)

  def Matrices(dim1: Int, dim2: Int) = new MatrixDom(dim1: Int, dim2: Int)

  def vector(values: Double*) = new DenseTensor1(values.toArray)

  def matrix(values: Seq[Double]*) = {
    val tmp = new DenseTensor2(values.length, values.head.length)

    (0 until values.length).foreach(row => {
      (0 until values(row).length).foreach(col => {
        tmp(row, col) = values(row)(col)
      })
    })

    tmp
  }


  def sigm[T <: DoubleTerm](term: T) = new Sigmoid(term)

  def sqrt[T <: DoubleTerm](term: T) = new Sqrt(term)

  def clip[T <: DoubleTerm](term: T) = new Clip(term)

  def tanh[T <: DoubleTerm](term: T) = new Tanh(term)

  def log[T <: DoubleTerm](term: T) = new Log(term)

  //  def I[T <: BoolTerm](term: T) = new Iverson(term)
  def I(term: BoolTerm) = new Iverson(term)

  def sigmVec[T <: VectorTerm](term: T) = new VectorSigmoid(term)

  def tanhVec[T <: VectorTerm](term: T) = new VectorTanh(term)

  def min(arg1: DoubleTerm, arg2: DoubleTerm) = new Min2[DoubleTerm](arg1, arg2)

  def max(arg1: DoubleTerm, arg2: DoubleTerm) = new Max2[DoubleTerm](arg1, arg2)

  def l1[T <: VectorTerm](term: T) = new L1Norm[term.type](term)

  def l2[T <: VectorTerm](term: T) = sqrt(term dot term)

  def l2Squared[T <: VectorTerm](term: T) = term dot term

  implicit class RichVectTerm(val vect: VectorTerm) {
    def dot(that: VectorTerm) = new DotProduct(vect, that)

    def *(that: Term[DoubleDom]) = new VectorScaling(vect, that)

    def +(that: VectorTerm) = new VectorSum(IndexedSeq(vect, that))

    def -(that: VectorTerm) = new VectorSum(IndexedSeq(vect, that * (-1.0)))


    //element-wise addition
    def :+(that: VectorTerm): VectorTerm = ???

    //element-wise multiplication
    def :*(that: VectorTerm): VectorTerm = ???

    def cons(that: VectorTerm): VectorTerm = new VectorConcatenation(vect, that)

    def l2(mask: VectorTerm = null): DoubleTerm = new SparseL2(vect, mask)
  }

  implicit class RichMatrixTerm(val mat: Term[MatrixDom]) {
    def dot(that: Term[MatrixDom]) = new MatrixDotProduct(mat, that)

    def *(that: VectorTerm) = new MatrixVectorProduct(mat, that)
  }

  implicit class RichDoubleTerm(term: DoubleTerm) {
    def +(that: DoubleTerm) = Dom.doubles.own(new Sum(IndexedSeq(term, that)))

    def -(that: DoubleTerm) = Dom.doubles.own(new Sum(IndexedSeq(term, that * (-1.0))))

    //def *(that: DoubleTerm): Product = new Product(IndexedSeq(term, that))

    def *(that: DoubleTerm) = Dom.doubles.own(new Product(IndexedSeq(term, that)))

    //    def *(that: IntTerm): Product = new Product(IndexedSeq(term, new IntToDouble(that)))

    def *(that: VectorTerm) = new VectorScaling(that, term)

    def /(that: DoubleTerm) = Dom.doubles.own(new Div(term, that))

    def unary_- = term * (-1.0)

    def argmaxBy(factory: ArgmaxerFactory) = new ProxyTerm[TypedDom[Double]] {
      def self = term

      override def argmaxer(wrt: Seq[Var[Dom]]) = factory.argmaxer(term, wrt)

      def copy(args: IndexedSeq[ArgumentType]) = ???

    }

    def argmaxBy(factory: ArgmaxerFactory2) = new ProxyTerm[TypedDom[Double]] {
      def self = term

      override def argmaxerImpl(wrt: Seq[Var[Dom]])(observed: Settings, msgs: Msgs) = {
        factory.argmaxer(term, wrt)(observed, msgs)
      }

      def copy(args: IndexedSeq[ArgumentType]) = ???

    }


  }

  implicit class StringEquals(val t:Any) {
    def stringEquals(that:Any) = t.toString == that.toString
  }

  implicit class RichIntTerm(term: IntTerm) {
    def +(that: IntTerm) = new BinaryIntFun(term, that, _ + _)

    def -(that: IntTerm) = new BinaryIntFun(term, that, _ - _)

    def *(that: IntTerm) = new BinaryIntFun(term, that, _ * _)

  }


  def argmax[D <: Dom](dom: D)(obj: dom.Var => DoubleTerm): Argmax[dom.type] = {
    val variable = dom.variable("_hidden")
    val term = obj(variable)
    new Argmax[dom.type](term, variable)
  }

  def argmax2[D <: Dom](dom: D)(obj: dom.Var => DoubleTerm): Argmax2[dom.type] = {
    val variable = dom.variable("_hidden")
    val term = obj(variable)
    new Argmax2[dom.type](term, variable)
  }


  def max[D <: Dom](dom: D)(obj: dom.Var => DoubleTerm) = {
    val variable = dom.variable("_hidden")
    val term = obj(variable)
    new Max(term, Seq(variable))
  }

  def sum[T](dom: Seq[T])(arg: T => DoubleTerm) = new Sum(dom.toIndexedSeq.map(arg))

  def sum(args: DoubleTerm*) = new Sum(args.toIndexedSeq)

  def varSeqSum[T <: Term[VarSeqDom[TypedDom[Double]]]](args: T) = new VarSeqSum[TypedDom[Double], T](args)

  def sum2[E <: Dom, T <: Term[VarSeqDom[E]], Body <: DoubleTerm](indices: T)(body: indices.domain.elementDom.Var => Body) = {
    val variable = indices.domain.elementDom.variable("_i")
    val instantiatedBody = body(variable)
    new FirstOrderSum[E, Body, T](indices, variable, instantiatedBody)
  }

  def sum[T <: Term[VarSeqDom[Dom]], Body <: DoubleTerm](indices: T)(body: indices.domain.elementDom.Var => Body) =
    sum2[Dom, Term[VarSeqDom[Dom]], Body](indices)(body)


  def oneHot(index: Int, value: Double = 1.0)(implicit dom: VectorDom) =
    dom.Const(new SingletonTensor1(dom.dim, index, value))


  implicit def doubleToConstant(d: Double): Constant[DoubleDom] = Dom.doubles.Const(d)

  implicit def intToConstant(i: Int): Constant[IntDom] = Dom.ints.Const(i)

  implicit def doubleToRichConstant(d: Double): RichDoubleTerm = new RichDoubleTerm(doubleToConstant(d))


  implicit def intToDoubleConstant(d: Int): Constant[DoubleDom] = Dom.doubles.Const(d)

  implicit def vectToConstant(d: FactorieVector): Constant[VectorDom] = Vectors(d.dim1).Const(d)

  implicit def vectToConstantWithDom(d: FactorieVector)(implicit dom: VectorDom): dom.Term = dom.Const(d)

  implicit def dynVectToConstantWithDom(d: Dynamic[FactorieVector])(implicit dom: VectorDom): dom.Term = dom.dynConst(d)

  implicit def matToConstant(d: FactorieMatrix): Constant[MatrixDom] = Matrices(d.dim1, d.dim2).Const(d)

  implicit def intToDouble(int: IntTerm): IntToDouble[int.type] = new IntToDouble[int.type](int)

  def boolToInt(bool: BoolTerm):Dom.ints.Term = {
    import TermImplicits._
    bool.map(b => if (b) 1 else 0)(Dom.ints)
  }

}

trait Stochastic {

  import TermImplicits._

  def sampleSequential(range: Range) = Ints(range).sequential
  def sampleUniform(range: Range)(implicit random:Random) = Ints(range).uniform

}

trait LoggedTerms extends NamedValueImplicits {

  def logged[T <: Term[Dom]](term:NamedValue[T]):term.value.domain.Term = {
    val clean = term.name.replace("ml.wolfe.term.TermImplicits.","")
    term.value.domain.own(new LogTerm[Dom, Term[Dom]](term.value, clean).asInstanceOf[TypedTerm[term.value.domain.Value]])
  }

}



