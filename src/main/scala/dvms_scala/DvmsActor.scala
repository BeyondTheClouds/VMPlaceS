package dvms_scala


import _root_.entropy._
import _root_.entropy.NegativeResultOfComputation
import _root_.entropy.PositiveResultOfComputation
import _root_.scheduling.dvms2.{SGActor, SGNodeRef}
import java.util.UUID
import org.simgrid.msg.Msg
import org.simgrid.trace.Trace
import org.simgrid.msg.Host
import scala.Some
import simulation.SimulatorManager

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/14/13
 * Time: 4:43 PM
 * To change this template use File | Settings | File Templates.
 */


// Routing messages
case class ToMonitorActor(msg: Any)

case class ToDvmsActor(msg: Any)

case class ToEntropyActor(msg: Any)


case class ThisIsYourNeighbor(neighbor: SGNodeRef)

case class CpuViolationDetected()

case class ComputerSpecification(numberOfCPU: Int, ramCapacity: Int, coreCapacity: Int)

case class PhysicalNode(ref: SGNodeRef, machines: List[VirtualMachine], specs: ComputerSpecification)

case class VirtualMachine(name: String, cpuConsumption: Double, specs: ComputerSpecification)

// Message used for the base of DVMS
case class DissolvePartition()

object DvmsPartition {
    def apply(leader: SGNodeRef, initiator: SGNodeRef, nodes: List[SGNodeRef], state: DvmsPartititionState): DvmsPartition = DvmsPartition(leader, initiator, nodes, state, UUID.randomUUID())
}

case class DvmsPartition(leader: SGNodeRef, initiator: SGNodeRef, nodes: List[SGNodeRef], state: DvmsPartititionState, id: UUID)

case class TransmissionOfAnISP(currentPartition: DvmsPartition)

case class IAmTheNewLeader(partition: DvmsPartition, firstOut: SGNodeRef)

// Message used for the merge of partitions
case class IsThisVersionOfThePartitionStillValid(partition: DvmsPartition)

case class CanIMergePartitionWithYou(partition: DvmsPartition, contact: SGNodeRef)

case class ChangeTheStateOfThePartition(newState: DvmsPartititionState)

// Message for the resiliency
case class EverythingIsOkToken(id: UUID)

case class VerifyEverythingIsOk(id: UUID, count: Int)

class DvmsPartititionState(val name: String) {

    def getName(): String = name

    def isEqualTo(a: DvmsPartititionState): Boolean = {
        this.name == a.getName
    }

    def isDifferentFrom(a: DvmsPartititionState): Boolean = {
        this.name != a.getName
    }
}

case class Created() extends DvmsPartititionState("Created")

case class Blocked() extends DvmsPartititionState("Blocked")

case class Growing() extends DvmsPartititionState("Growing")

case class Destroyed() extends DvmsPartititionState("Destroyed")

object DvmsActor {
}

class DvmsActor(applicationRef: SGNodeRef) extends SGActor(applicationRef) {

    // by default, a node is in a ring containing only it self
    var nextDvmsNode: SGNodeRef = applicationRef

    // Variables that are specific to a node member of a partition
    var firstOut: Option[SGNodeRef] = None
    var currentPartition: Option[DvmsPartition] = None

    // Variables for the resiliency
    var countOfCheck: Option[(UUID, Int)] = None

    def log(msg: String) {
        //        Msg.info(s"[${applicationRef.getName}] $msg")
    }

    val entropyActor = new EntropyActor(applicationRef)

    def mergeWithThisPartition(partition: DvmsPartition) {

        log(s"merging $partition with ${currentPartition.get}")
        currentPartition = Some(DvmsPartition(
            currentPartition.get.leader,
            currentPartition.get.initiator,
            currentPartition.get.nodes ::: partition.nodes,
            Growing(), UUID.randomUUID()))

        currentPartition.get.nodes.foreach(node => {
            log(s"(a) $applicationRef: sending ${IAmTheNewLeader(currentPartition.get, firstOut.get)} to $node")
            send(node, ToDvmsActor(IAmTheNewLeader(currentPartition.get, firstOut.get)))
        })

        if (computeAndApplyReconfiguration()) {
            log(s"(1) the partition $currentPartition is enough to reconfigure")

            log(s"(a) I decide to dissolve $currentPartition")
            currentPartition.get.nodes.foreach(node => {
                send(node, ToDvmsActor(DissolvePartition()))
            })
        } else {

            log(s"(1a) the partition $currentPartition is not enough to reconfigure," +
                s" I try to find another node for the partition, deadlock? ${currentPartition.get.nodes.contains(firstOut)}")

            log(s"(1) $applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
            send(firstOut.get, ToDvmsActor(TransmissionOfAnISP(currentPartition.get)))
        }
    }

    def changeCurrentPartitionState(newState: DvmsPartititionState) {
        currentPartition match {
            case Some(partition) => currentPartition = Some(DvmsPartition(partition.leader, partition.initiator, partition.nodes, newState, partition.id))
            case None =>
        }
    }

    var lockedForFusion: Boolean = false


    def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef): Unit = {

        //        if (message.toString != "CpuViolationDetected()")
        //            println(s"${applicationRef.getName} received: $message")

        message match {


            case IsThisVersionOfThePartitionStillValid(partition) => {
                currentPartition match {
                    case Some(p) => send(sender, partition.id.equals(currentPartition.get.id))
                    case None => send(sender, false)
                }
            }

            case VerifyEverythingIsOk(id, count) => {
                countOfCheck match {
                    case Some((pid, pcount)) if (pid == id) => {
                        // the predecessor in the partition order has crashed
                        if (!(count > pcount)) {
                            currentPartition match {
                                case Some(p) => {
                                    (p.nodes.indexOf(applicationRef) match {
                                        case i: Int if (i == 0) => p.nodes(p.nodes.size - 1)
                                        case i: Int => p.nodes(i - 1)
                                    }) match {
                                        // the initiator of the partition has crashed
                                        case node: SGNodeRef if (node isEqualTo p.initiator) => {

                                            log(s"$applicationRef: The initiator has crashed, I am becoming the new leader of $currentPartition")

                                            // the partition will be dissolved
                                            p.nodes.filterNot(n => n isEqualTo node).foreach(n => {
                                                send(n, ToDvmsActor(DissolvePartition()))
                                            })
                                        }

                                        // the leader or a normal node of the partition has crashed
                                        case node: SGNodeRef => {

                                            // creation of a new partition without the crashed node
                                            val newPartition: DvmsPartition = new DvmsPartition(p.initiator, applicationRef, p.nodes.filterNot(n => n isEqualTo node), p.state, UUID.randomUUID())

                                            currentPartition = Some(newPartition)
                                            firstOut = Some(nextDvmsNode)


                                            log(s"$applicationRef: A node crashed, I am becoming the new leader of $currentPartition")

                                            newPartition.nodes.foreach(node => {
                                                send(node, ToDvmsActor(IAmTheNewLeader(newPartition, firstOut.get)))
                                            })

                                            countOfCheck = Some((newPartition.id, -1))
                                            send(self(), VerifyEverythingIsOk(newPartition.id, 0))
                                        }
                                    }
                                }
                                case None =>
                            }
                        }
                    }
                    case None =>
                }
            }

            case EverythingIsOkToken(id) => {
                currentPartition match {
                    case Some(p) if (p.id == id) => {
                        val nextSGNodeRef: SGNodeRef = p.nodes.indexOf(applicationRef) match {
                            case i: Int if (i == (p.nodes.size - 1)) => p.nodes(0)
                            case i: Int => p.nodes(i + 1)
                        }

                        countOfCheck = Some((p.id, countOfCheck.get._2 + 1))


                        // TODO: j'attends un peu de temps avant de vérifier si un message a été envoyé,
                        // si ce n'est pas le cas, alors on considère que c'est pas normal
                        //               context.system.scheduler.scheduleOnce((2*DvmsActor.PeriodOfPartitionNodeChecking), self, VerifyEverythingIsOk(id, countOfCheck.get._2+1))
                        //               context.system.scheduler.scheduleOnce((DvmsActor.PeriodOfPartitionNodeChecking/(p.nodes.size)), nextSGNodeRef.ref, ToDvmsActor(EverythingIsOkToken(id)))
                    }
                    case _ =>
                }
            }

            case CanIMergePartitionWithYou(partition, contact) => {

                send(sender, (!lockedForFusion))

                if (!lockedForFusion) {
                    lockedForFusion = true
                }
            }

            case DissolvePartition() => {

                /* Simgrid MSG related code */
                // TODO Adrien -> Adrien, the current approach is not robust enough, NB_MC decreases if and only if
                // the initiator receives the  dissolution message.
                if (applicationRef.getName == currentPartition.get.initiator.getName) {
                    Msg.info(applicationRef.getName() + ": ISP ends");
                    Trace.hostVariableSub(SimulatorManager.getServiceNodeName, "NB_MC", 1);
                }
                Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "free");
                /* End of Simgrid MSG related code */


                currentPartition match {
                    case Some(p) => {
                        log(s"$applicationRef: I dissolve the partition $p")
                    }
                    case None => log(s"$applicationRef: I dissolve the partition None")
                }


                firstOut = None
                currentPartition = None
                lockedForFusion = false
            }

            case IAmTheNewLeader(partition, firstOutOfTheLeader) => {

                log(s"$applicationRef: ${partition.leader} is the new leader of $partition")

                currentPartition = Some(partition)
                lockedForFusion = false

                countOfCheck = Some((partition.id, -1))

                firstOut match {
                    case None => firstOut = Some(firstOutOfTheLeader)
                    case Some(node) => {
                        if (firstOut.get isEqualTo partition.leader) {
                            firstOut = Some(firstOutOfTheLeader)
                        }
                    }
                }
            }

            case ChangeTheStateOfThePartition(newState) => {
                changeCurrentPartitionState(newState)
            }

            case msg@TransmissionOfAnISP(partition) => {

                log(s"received an ISP: $msg @$currentPartition")

                currentPartition match {
                    case Some(p) => p match {
                        // the ISP went back to it's initiator for the first time
                        case _ if ((partition.initiator isEqualTo p.initiator)
                            && (partition.state isEqualTo Growing())) => {

                            log(s"$applicationRef: the partition $partition went back to it's initiator" +
                                s" with a Growing state: it becomes blocked :s")


                            changeCurrentPartitionState(Blocked())

                            // the state of the current partition become Blocked()
                            p.nodes.foreach(node => {
                                send(node, ToDvmsActor(ChangeTheStateOfThePartition(Blocked())))
                            })

                            send(firstOut.get, ToDvmsActor(TransmissionOfAnISP(currentPartition.get)))

                        }
                        // the ISP went back to it's initiator for the second time
                        case _ if ((partition.initiator isEqualTo p.initiator)
                            && (partition.state isEqualTo Blocked())) => {

                            log(s"$applicationRef: the partition $partition went back to it's initiator" +
                                s" with a Blocked state: it dissolve it :(")
                            // the currentPartition should be dissolved
                            p.nodes.foreach(node => {
                                send(node, ToDvmsActor(DissolvePartition()))
                            })

                        }
                        // the incoming ISP is different from the current ISP and the current state is not Blocked
                        case _ if ((partition.initiator isDifferentFrom p.initiator)
                            && (p.state isEqualTo Growing())) => {

                            log(s"$applicationRef: forwarding $msg to $firstOut")

                            // I forward the partition to the current firstOut
                            forward(firstOut.get, sender, ToDvmsActor(msg))

                        }
                        // the incoming ISP is different from the current ISP and the current state is Blocked
                        //   ==> we may merge!
                        case _ if ((partition.initiator isDifferentFrom p.initiator)
                            && (p.state isEqualTo Blocked())) => {

                            partition.state match {
                                case Blocked() => {

                                    if (partition.initiator isSuperiorThan p.initiator) {
                                        log(s"$applicationRef: may merge $p with $partition")


                                        lockedForFusion = true
                                        //                           val willMerge:Boolean = Await.result(sender ? CanIMergePartitionWithYou(p, applicationRef), 1 second).asInstanceOf[Boolean]
                                        val willMerge: Boolean = ask(sender, CanIMergePartitionWithYou(p, applicationRef)).asInstanceOf[Boolean]

                                        log(s"$applicationRef got a result $willMerge")

                                        willMerge match {
                                            case true => {
                                                lockedForFusion = true

                                                log(s"$applicationRef is effectively merging partition $p with $partition")

                                                mergeWithThisPartition(partition)
                                            }
                                            case false =>
                                        }
                                    } else {

                                        // the order between nodes is not respected, the ISP should be forwarded
                                        log(s"$applicationRef: order between nodes is not respected, I forward $partition to $firstOut")
                                        forward(firstOut.get, sender, ToDvmsActor(msg))
                                    }

                                }
                                case Growing() => {
                                    log(s"$applicationRef: forwarding $msg to $firstOut")
                                    forward(firstOut.get, sender, ToDvmsActor(msg))
                                }
                            }
                        }
                        // other case... (if so)
                        case _ => {

                        }
                    }

                    case None => {

                        var partitionIsStillValid: Boolean = true

                        if (partition.state isEqualTo Blocked()) {
                            try {
                                //                	  partitionIsStillValid = Await.result(partition.initiator.ref ? ToDvmsActor(IsThisVersionOfThePartitionStillValid(partition)), 1 second).asInstanceOf[Boolean]

                                partitionIsStillValid = ask(partition.initiator, ToDvmsActor(IsThisVersionOfThePartitionStillValid(partition))).asInstanceOf[Boolean]

                            } catch {
                                case e: Throwable => {
                                    partitionIsStillValid = false
                                }
                            }

                        }

                        if (partitionIsStillValid) {
                            /* SimGrid MSG Related code */
                            Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "booked");
                            /* End of SimGrid MSG Related code */
                            // the current node is becoming the leader of the incoming ISP
                            log(s"$applicationRef: I am becoming the new leader of $partition")

                            val newPartition: DvmsPartition = new DvmsPartition(applicationRef, partition.initiator, applicationRef :: partition.nodes, Growing(), UUID.randomUUID())

                            currentPartition = Some(newPartition)
                            firstOut = Some(nextDvmsNode)

                            partition.nodes.foreach(node => {
                                send(node, ToDvmsActor(IAmTheNewLeader(newPartition, firstOut.get)))
                            })

                            countOfCheck = Some((newPartition.id, -1))
                            send(self(), VerifyEverythingIsOk(newPartition.id, 0))

                            // ask entropy if the new partition is enough to resolve the overload
                            if (computeAndApplyReconfiguration()) {

                                // it was enough: the partition is no more useful
                                currentPartition.get.nodes.foreach(node => {
                                    send(node, ToDvmsActor(DissolvePartition()))
                                })
                            } else {
                                // it was not enough: the partition is forwarded to the firstOut
                                send(firstOut.get, ToDvmsActor(TransmissionOfAnISP(currentPartition.get)))
                            }
                        } else {
                            log(s"$applicationRef: $partition is no more valid (source: ${partition.initiator})")
                        }
                    }
                }
            }

            case CpuViolationDetected() => {
                currentPartition match {
                    case None => {
                        log("Dvms has detected a new cpu violation")

                        /* Simgrid MSG related code */
                        Msg.info(applicationRef.getName() + ": ISP starts");
                        Trace.hostVariableAdd(SimulatorManager.getServiceNodeName, "NB_MC", 1);
                        Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "booked");
                        /* End of Simgrid MSG related code */

                        firstOut = Some(nextDvmsNode)

                        currentPartition = Some(DvmsPartition(applicationRef, applicationRef, List(applicationRef), Growing(), UUID.randomUUID()))

                        log(s"$applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
                        send(nextDvmsNode, ToDvmsActor(TransmissionOfAnISP(currentPartition.get)))
                    }
                    case _ =>
                }
            }

            case ToDvmsActor(msg) => {
                receive(msg.asInstanceOf[Object], sender, returnCanal)
                //         false
            }

            case ThisIsYourNeighbor(node) => {
                log(s"my neighbor has changed: $node")
                nextDvmsNode = node
            }

            case msg => {
                log(s"received an unknown message <$msg>")
                //         forward(applicationRef, sender, msg)
            }
        }
    }


    def computeAndApplyReconfiguration(): Boolean = {

        /* Simgrid MSG related code */
        // TODO Adrien -> Jonathan, Please display the origin of the event (as it was previously done)
        //        Msg.info("Launching scheduler on "+applicationRef.getName()+" evt on "+event.getOrigin());
        Msg.info("Launching scheduler on "+applicationRef.getName()+" evt on");
        Trace.hostPushState(applicationRef.getName(), "SERVICE", "compute");
        /* End of Simgrid MSG related code */

        log(s"will compute a reconfiguration plan with $currentPartition")

        val res = entropyActor.computeReconfigurationPlan(currentPartition.get.nodes)

        var timeToCompute: Long = 0L
        // TODO Adrien -> Jonathan Why a quite similar code to get the time ?
        // What is the purpose of this ComputationInformation class ?
        res match {
            case PositiveResultOfComputation(p, ComputationInformation(c, mc, gd, ttc)) => {
                timeToCompute = ttc
            }
            case NegativeResultOfComputation(ComputationInformation(c, mc, gd, ttc)) => {
                timeToCompute = ttc

            }
        }

      /* Simgrid MSG related code */
        if (timeToCompute / 1000 > 0) {
            waitFor(timeToCompute / 1000);
        } else {
            waitFor(0.5);
        }

        Msg.info("Computation time: " + timeToCompute / 1000);
        Trace.hostPopState(Host.currentHost().getName(), "SERVICE");
        /* End of Simgrid MSG related code */



        res match {
            case PositiveResultOfComputation(plan, information) => {
                log(s"the partition $currentPartition was sufficient to resolve the overloading");

              /* Simgrid MSG related code */
                Msg.info("Cost of reconfiguration:" + information.cost + ", time: " + (information.cost / 10000));
                Trace.hostPushState(applicationRef.getName, "SERVICE", "reconfigure");
                Trace.hostVariableAdd(applicationRef.getName, "NB_MIG", information.migrationCount);
                Trace.hostVariableAdd("node0", "NB_MIG", information.migrationCount);
              /* End of Simgrid MSG related code */

                entropyActor.applyReconfigurationPlan(plan)

                true
            }
            case NegativeResultOfComputation(information) => {

                log(s"the partition $currentPartition was not sufficient to resolve the overloading")
              /* Simgrid MSG related code */
                Msg.info("No solution found, the event will be forwarded");
              /* End of Simgrid MSG related code */

                false
            }
        }
    }

}