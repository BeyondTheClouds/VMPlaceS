package scheduling.entropyBased.dvms2.dvms.dvms2

/* ============================================================
 * Discovery Project - DVMS
 * http://beyondtheclouds.github.io/
 * ============================================================
 * Copyright 2013 Discovery Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============================================================ */


import org.simgrid.msg.{Process, Msg}
import org.discovery.dvms.entropy.EntropyMessage
import scheduling.entropyBased.dvms2.{DvmsProperties, DVMSProcess, SGNodeRef, SGActor}
import configuration.XVM
import simulation.SimulatorManager
import org.discovery.DiscoveryModel.model.ReconfigurationModel._
import scheduling.entropyBased.dvms2.overlay.SimpleOverlay
import scala.collection.JavaConversions._
import scheduling.entropyBased.dvms2.dvms.dvms2.DvmsModel._
import scheduling.entropyBased.dvms2.dvms.dvms2.DvmsProtocol._
import scheduling.entropyBased.dvms2.dvms.dvms2.DvmsModel.DvmsPartititionState._
import org.discovery.dvms.entropy.EntropyProtocol.ComputeAndApplyPlan
import scheduling.entropyBased.dvms2.dvms.timeout.TimeoutProtocol.{EnableTimeoutSnoozing, DisableTimeoutSnoozing, WorkOnThisPartition}

object DvmsActor {
  val partitionUpdateTimeout: Double = 4.5
}

class DvmsActor(applicationRef: SGNodeRef, parentProcess: DVMSProcess, entropyActorRef: SGNodeRef, snoozerActorRef: SGNodeRef) extends SGActor(applicationRef) {

  /* Local states of a DVMS agent */

  def firstOut: Option[SGNodeRef] = {
    val filter: List[String] = currentPartition match {
      case Some(p) =>
        val nodes: List[SGNodeRef] = p.nodes
        nodes.map(n => n.getName)
      case None => Nil
    }
    SimpleOverlay.giveSomeNeighbour(filter)
  }

  var currentPartition: Option[DvmsPartition] = None
  var lastPartitionUpdateDate: Option[Double] = None
  var lockedForFusion: Boolean = false

  def isPerformingMigrations: Boolean = {
    val vms: java.util.Collection[XVM] = SimulatorManager.getXHostByName(applicationRef.getName).getRunnings
    ! vms.toList.forall(vm => !vm.isMigrating)
  }

  /* Methods and functions related to logs */

  def logInfo(msg: String) {
    Msg.info(s"$msg")
  }

  def logWarning(msg: String) {
    Msg.info(s"$msg")
  }

  /* Methods and functions related to partition management */

  def isThisPartitionStillValid(partition: DvmsPartition): Boolean = {
    val response = currentPartition match {
      case Some(p) =>
        p.id == partition.id && p.version <= partition.version
      case None =>
        false
    }
    logInfo(s"$applicationRef@$currentPartition isThisPartitionStillValid($partition)? => $response")
    response
  }

  def updatePartitionOnAllNodes(partition: DvmsPartition) {
    currentPartition = Some(partition)
    snoozeTimeout()
    partition.nodes.filterNot(member => member.getId == self().getId).foreach(member => {
      ask(member, SetCurrentPartition(partition))
    })
  }

  def dissolvePartition(id: String, reason: String) {
    currentPartition match {
      case Some(partition) if(partition.id == id) =>
        logInfo(s"$applicationRef: I dissolve the partition $currentPartition, because <$reason>")

        lockedForFusion = false
        lastPartitionUpdateDate = None
        currentPartition = None

      case _ =>
    }
  }

  def mergeWithThisPartition(partition: DvmsPartition) {

    logInfo(s"merging $partition with ${currentPartition.get}")
    currentPartition = Some(DvmsPartition(
      currentPartition.get.leader,
      currentPartition.get.initiator,
      currentPartition.get.nodes ::: partition.nodes,
      Growing()))

    lastPartitionUpdateDate = Some(Msg.getClock)

    val nodes: List[SGNodeRef] = currentPartition.get.nodes
    nodes.filterNot(member => member.getId == self().getId).foreach(node => {
      logInfo(s"(a) $applicationRef: sending a new version of the partition ${SetCurrentPartition(currentPartition.get)} to $node")
      ask(node, SetCurrentPartition(currentPartition.get))
    })

    val computationResult = computeEntropy()
    computationResult match {
      case solution: ReconfigurationSolution => {
        logInfo(s"(1) the partition $currentPartition is enough to reconfigure")

//        applySolution(solution)


        logInfo(s"(a) I decide to dissolve $currentPartition")
        val nodes: List[SGNodeRef] =currentPartition.get.nodes
        nodes.foreach(node => {
          send(node, DissolvePartition(currentPartition.get.id, "violation resolved"))
        })
      }
      case ReconfigurationlNoSolution() => {
        val nodes: List[SGNodeRef] = currentPartition.get.nodes
        logInfo(s"(1a) the partition $currentPartition is not enough to reconfigure," +
          s" I try to find another node for the partition, deadlock? ${nodes.contains(firstOut)}")

        firstOut match {
          case Some(existingNode) =>
            logInfo(s"(Y) $applicationRef transmitting a new ISP ${currentPartition.get} to neighbour: $existingNode")
            send(existingNode, TransmissionOfAnISP(currentPartition.get))
          case None =>
            logInfo(s"(Y) $applicationRef transmitting a new ISP ${currentPartition.get} to nobody")
        }
      }
    }
  }

  def changeCurrentPartitionState(newState: DvmsPartititionState) {
    currentPartition match {
      case Some(partition) =>
        val newPartition = DvmsPartition(
          partition.leader,
          partition.initiator,
          partition.nodes,
          newState,
          partition.id,
          partition.version + 1
        )
        updatePartitionOnAllNodes(newPartition)

      case None =>
    }
  }

  def updateThePartitionWith(remotePartition: DvmsPartition) {
    currentPartition match {
      case Some(partition) if (partition.id == remotePartition.id) && (partition.version < remotePartition.version) =>
        currentPartition = Some(remotePartition)
        snoozeTimeout()
        true

      case None =>
        currentPartition = Some(remotePartition)
        snoozeTimeout()
        true

      case _ =>
        false
    }
  }


  /* Methods and functions related to ISP reception */

  def isOutdatedUpdate(remotePartition: DvmsPartition): Boolean = {
    (currentPartition, remotePartition.state) match {
      case (None, Finishing()) => true
      case (Some(p), _) if (p.state isEqualTo Finishing()) => true
      case _ => false
    }
  }

  def receivedAnIspWhenFree(sender: SGNodeRef, remotePartition: DvmsPartition, msg: Object) {
    var partitionIsStillValid: Boolean = true

    try {
      partitionIsStillValid = ask(remotePartition.initiator, IsThisVersionOfThePartitionStillValid(remotePartition)).asInstanceOf[Boolean]

    } catch {
      case e: Throwable => {
        logInfo(s"Partition $remotePartition is no more valid (Exception")
        e.printStackTrace()
        partitionIsStillValid = false
      }
    }

    logInfo(s"check if $remotePartition is still valid? $partitionIsStillValid")

    if (!isPerformingMigrations && partitionIsStillValid) {

      // the current node is becoming the leader of the incoming ISP
      val newPartition: DvmsPartition = new DvmsPartition(
        applicationRef,
        remotePartition.initiator,
        applicationRef :: remotePartition.nodes,
        Growing(),
        remotePartition.id,
        remotePartition.version + 1
      )


      logInfo(s"$applicationRef: I am becoming the new leader of $newPartition")

      updatePartitionOnAllNodes(newPartition)

      // ask entropy if the new partition is enough to resolve the overload
      val planApplicationProcess = new Process(parentProcess.getHost, parentProcess.getHost.getName + "-plan-application", new Array[String](0)) {
        def main(args: Array[String]) {
          val computationResult = computeEntropy()

          computationResult match {
            case solution: ReconfigurationSolution => {
              logInfo("(A) Partition was enough to reconfigure ")

              changeCurrentPartitionState(Finishing())

              updatePartitionOnAllNodes(currentPartition.get)

              // it was enough: the partition is no more useful
              val nodes: List[SGNodeRef] =currentPartition.get.nodes
              nodes.filterNot(n => n.getId == applicationRef.getId).foreach(node => {
                ask(node, DissolvePartition(currentPartition.get.id, "reconfigurationPlan applied"))
              })
              dissolvePartition(currentPartition.get.id, "reconfigurationPlan applied")
            }
            case ReconfigurationlNoSolution() => {

              firstOut match {
                case Some(existingNode) =>
                  logInfo(s"(A) Partition was not enough to reconfigure, forwarding to $existingNode")
                  changeCurrentPartitionState(Growing())
                  ask(existingNode, TransmissionOfAnISP(currentPartition.get))
                case None =>
                  logInfo(s"(A) $applicationRef : ${currentPartition.get} was not forwarded to nobody")
              }
            }
          }
        }
      }
      planApplicationProcess.start()

    } else {
      logWarning(s"$applicationRef: $remotePartition is no more valid (source: ${remotePartition.initiator})")
    }
  }

  def receivedAnIspWhenBooked(sender: SGNodeRef, remotePartition: DvmsPartition, msg: Object) {

    firstOut match {
      case Some(nextNode) =>
        currentPartition match {
          case Some(p) =>
            val nodes: List[SGNodeRef] = p.nodes
            (p.state, remotePartition.state) match {
              case (_, Growing()) =>
                if (remotePartition.initiator.getId == self().getId) {
                  changeCurrentPartitionState(Blocked())
                } else {
                  forward(nextNode, sender, TransmissionOfAnISP(remotePartition))
                }

              case (Blocked(), Blocked()) =>
                if (remotePartition.initiator.getId == self().getId) {
                  dissolvePartition(currentPartition.get.id, "blocked partition went back to its initiator :(")
                } else {
                  mergeWithThisPartition(remotePartition)
                }
              case _ =>
                forward(nextNode, sender, TransmissionOfAnISP(remotePartition))
            }
        }
      case _ =>
        throw new RuntimeException(s"cannot forward $remotePartition to unknown firstOut")
    }
  }

  /* Methods and functions related to fault tolerance */

  def snoozeTimeout() {
    lastPartitionUpdateDate = Some(Msg.getClock)
  }

  def checkTimeout() {
    (currentPartition, lastPartitionUpdateDate) match {
      case (Some(p), Some(d)) => {
        val duration: Double = (Msg.getClock - d)
        if (duration > DvmsActor.partitionUpdateTimeout) {
          logInfo(s"$applicationRef: timeout of $duration at partition $currentPartition has been reached: I dissolve everything")
          p.state match {
            case Growing() =>
              println("timeout while partition is growing()!")
            case _ =>

          }
          val nodes: List[SGNodeRef] = p.nodes
          nodes.filterNot(ref => ref.getId == applicationRef.getId).foreach(n => {
            send(n, DissolvePartition(p.id, "timeout"))
          })
          dissolvePartition(p.id, "timeout")
        }
      }
      case _ =>
    }
  }

  /* Methods and functions related to reconfiguration plans and migrations */

  def computeEntropy(): ReconfigurationResult = {

    if(currentPartition.get.nodes.size < DvmsProperties.getMinimumPartitionSize) {
      return ReconfigurationlNoSolution()
    }

    send(snoozerActorRef, WorkOnThisPartition(currentPartition.get.nodes))
    send(snoozerActorRef, EnableTimeoutSnoozing())
    val result = ask(entropyActorRef, ComputeAndApplyPlan(currentPartition.get.nodes)).asInstanceOf[ReconfigurationResult]
    send(snoozerActorRef, DisableTimeoutSnoozing())
    return result
  }


  /* Messages reception loop */

  def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {

    case IsThisVersionOfThePartitionStillValid(partition) =>
      logInfo(s"send $sender that partition is still valid ${isThisPartitionStillValid(partition)}")
      send(returnCanal, isThisPartitionStillValid(partition))

    case SnoozeTimeout() =>
      snoozeTimeout()

    case "checkTimeout" =>
      checkTimeout()

    case CanIMergePartitionWithYou(partition, contact) =>
      send(sender, (!lockedForFusion))
      if (!lockedForFusion) {
        lockedForFusion = true
      }

    case DissolvePartition(id, reason) =>
      dissolvePartition(id, reason)
      send(returnCanal, true)

    case msg@SetCurrentPartition(partition: DvmsPartition) =>
      logInfo(s"received an ISP update: $msg @$currentPartition and @$firstOut")
      updateThePartitionWith(partition)
      send(returnCanal, true)

    case msg@TransmissionOfAnISP(remotePartition) =>
      logInfo(s"received an ISP: $msg @$currentPartition and @$firstOut")
      currentPartition match {
        case Some(p) =>
          receivedAnIspWhenBooked(sender, remotePartition, msg)

        case None =>
          receivedAnIspWhenFree(sender, remotePartition, msg)
      }

      send(returnCanal, true)

    case "overloadingDetected" =>
      currentPartition match {
        case None => if(!isPerformingMigrations) {
          logInfo("Dvms has detected a new cpu violation")

          currentPartition = Some(DvmsPartition(
            applicationRef,
            applicationRef,
            List(applicationRef),
            Growing()
          ))

          lastPartitionUpdateDate = Some(Msg.getClock)

          firstOut match {
            case Some(existingNode) =>
              logInfo(s"$applicationRef transmitting a new ISP ${currentPartition.get} to neighbour: $existingNode")
              send(existingNode, TransmissionOfAnISP(currentPartition.get))
            case None =>
              logInfo(s"$applicationRef transmitting a new ISP ${currentPartition.get} to nobody")
          }

        }
        case _ =>
        // The node is already in a partition => nothing should be done!
      }


    case entropyMessage: EntropyMessage =>
      forward(entropyActorRef, sender, entropyMessage)

    case msg =>
      println(s"unknown message: $msg")
  }
}


