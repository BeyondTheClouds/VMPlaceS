package entropy

import plan.TimedReconfigurationPlan
import scheduling.dvms2.{SGNodeRef, SGActor}

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/18/13
 * Time: 3:25 PM
 * To change this template use File | Settings | File Templates.
 */

case class EntropyComputeReconfigurePlan(nodes: List[SGNodeRef])

abstract class AbstractEntropyActor(applicationRef: SGNodeRef) extends SGActor(applicationRef) {


    def computeReconfigurationPlan(nodes: List[SGNodeRef]): ResultOfComputation
    def applyReconfigurationPlan(plan: TimedReconfigurationPlan)

    def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {
        case EntropyComputeReconfigurePlan(nodes) => {
//            send(sender, computeAndApplyReconfigurationPlan(nodes))
        }

        case msg => {
            //      send(applicationRef, msg)
        }
    }
}