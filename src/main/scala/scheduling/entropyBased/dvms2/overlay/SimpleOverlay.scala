package scheduling.entropyBased.dvms2.overlay

import scheduling.entropyBased.dvms2.SGNodeRef
import simulation.DistributedResolver
import configuration.XHost

/**
 * Created by jonathan on 17/07/14.
 */
case class Tuple(id: String, ref: SGNodeRef, resolver: DistributedResolver)

object SimpleOverlay {

  var listOfNodes: List[Tuple] = Nil

  def register(id: String, ref: SGNodeRef, resolver: DistributedResolver) {
    listOfNodes = Tuple(id, ref, resolver) :: listOfNodes
  }

//  def setCrashed(id: String) {
//    listOfNodes = listOfNodes.filterNot(x => x.id == id)
//  }

  def giveSomeNeighbour(filter: List[String]): Option[SGNodeRef] = {
    def isDead(t: Tuple): Boolean = t.resolver.getHost match {
      case xhost: XHost => xhost.isOff
      case _ => false
    }

    util.Random.shuffle(listOfNodes).filterNot(t => filter.contains(t.id)).filterNot(t => isDead(t)) match {
      case first :: l =>
        Some(first.ref)
      case _ =>
        None
    }
  }

}
