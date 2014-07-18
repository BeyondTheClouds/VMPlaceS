package scheduling.entropyBased.dvms2.overlay

import scheduling.entropyBased.dvms2.SGNodeRef
import simulation.DistributedResolver

/**
 * Created by jonathan on 17/07/14.
 */
case class Tuple(id: String, ref: SGNodeRef, resolver: DistributedResolver)

object SimpleOverlay {

  var listOfNodes: List[Tuple] = Nil

  def register(id: String, ref: SGNodeRef, resolver: DistributedResolver) {
    listOfNodes = Tuple(id, ref, resolver) :: listOfNodes
  }

  def setCrashed(id: String) {
    listOfNodes = listOfNodes.filterNot(x => x.id == id)
  }

  def giveSomeNeighbour(filter: List[String]): Option[SGNodeRef] = {
    listOfNodes.filterNot(h => filter.contains(h.id)) match {
      case first :: l =>
        Some(first.ref)
      case _ =>
        None
    }
  }

}
