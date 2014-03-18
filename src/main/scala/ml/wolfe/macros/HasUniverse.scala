package ml.wolfe.macros

import scala.reflect.api.Universe
import scala.reflect.macros.Context

/**
 * This trait represents classes that work with respect to a specific universe. If you have generic functionality
 * for universes, best to introduce a trait that is self-typed to be InUniverse,
 * see [[ml.wolfe.macros.Transformers]] for an example.
 *
 * @author Sebastian Riedel
 */
trait HasUniverse {
  type U<:Universe
  val universe:U
}


trait HasContext[C<:Context]{
  val context:C
}

