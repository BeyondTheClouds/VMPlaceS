package scheduling.distributed.dvms2.dvms.dvms3

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

import org.discovery.DiscoveryModel.model.ReconfigurationModel.{ReconfigurationResult, ReconfigurationlNoSolution}
import org.discovery.dvms.entropy.EntropyProtocol.ComputeAndApplyPlan
import org.simgrid.msg.{Host, Msg}
import scheduling.distributed.dvms2.dvms.dvms2.LoggingProtocol
import scheduling.distributed.dvms2.dvms.dvms2.LoggingProtocol._
import scheduling.distributed.dvms2.dvms.dvms3.LocalityBasedSchedulerProtocol._
import scheduling.distributed.dvms2.dvms.timeout.TimeoutProtocol.{DisableTimeoutSnoozing, EnableTimeoutSnoozing, WorkOnThisPartition}
import scheduling.distributed.dvms2.overlay.SimpleOverlay
import scheduling.distributed.dvms2.{DVMSProcess, DvmsProperties, SGActor, SGNodeRef}
import simulation.SimulatorManager

trait DvmsMessage

object LocalityBasedSchedulerProtocol {

  case class YouBelongToThisPartition(partition: Partition) extends DvmsMessage

  case class MergeWithThisPartition(partition: Partition) extends DvmsMessage

  case class Downgrade() extends DvmsMessage

  case class PartitionChanged(partition: Partition) extends DvmsMessage

  case class Exit() extends DvmsMessage

}

case class Partition(leader: MayFail[SGNodeRef], nodes: List[MayFail[SGNodeRef]])

object LocalityBasedScheduler {
  val partitionTimeout: Double = 3.5
}

class LocalityBasedScheduler(currentNode: SGNodeRef, parentProcess: DVMSProcess, entropyActorRef: SGNodeRef, snoozerActorRef: SGNodeRef) extends SGActor(currentNode) {

  class LocalityBasedSchedulerCore(host: Host, name: String, port: Int) extends org.simgrid.msg.Process(host, s"$name-lsb-core") {

    implicit val timeout = 2.5
    var isLeading = false
    var currentPartition: Option[Partition] = None
    var lastPartitionUpdate: Option[Double] = None

    def logInfo(msg: String) {
      Msg.info(s"$msg")
    }

    def updateLastUpdateTime() {
      lastPartitionUpdate = Some(Msg.getClock)
    }

    def enoughResources(): ReconfigurationResult = {
      val nodes = currentPartition.get.nodes.map(n => n.bind(x => x).get)

      if(nodes.size < DvmsProperties.getMinimumPartitionSize) {
        return ReconfigurationlNoSolution()
      }

      send(snoozerActorRef, WorkOnThisPartition(nodes))
      send(snoozerActorRef, EnableTimeoutSnoozing())
      val result = ask(entropyActorRef, ComputeAndApplyPlan(nodes)).asInstanceOf[ReconfigurationResult]
      send(snoozerActorRef, DisableTimeoutSnoozing())
      return result
    }

    def startIterativeScheduling() {

      isLeading = true
      currentPartition = Some(Partition(MayFail.unit(currentNode), List(MayFail.unit(currentNode))))
      lastPartitionUpdate = Some(Msg.getClock)

      // Alert LogginActor that a violation has been detected
      send(currentNode, ViolationDetected(Msg.getClock, s"${currentNode}"))

      logInfo(s"$currentNode: starting ISP")
      send(currentNode, IsBooked(Msg.getClock, s"${currentNode}"))

      do {
        logInfo(s"$currentNode: asking for a Node")
        val filter = currentPartition.get.nodes.map(n => n.bind(ref => s"${ref.getName}")).flatten
        SimpleOverlay.giveSomeNeighbour(filter) match {

          case Some(node: SGNodeRef) =>
            logInfo(s"$currentNode: got $node")
            val mayFailedNode = MayFail.unit(node)
            mayFailedNode.watch(failedNode => {
              logInfo("removing failed node")
              currentPartition = Some(Partition(MayFail.unit(currentNode),
                currentPartition.get.nodes.filter(mayFailedNode => failedNode != mayFailedNode)))
              lastPartitionUpdate = Some(Msg.getClock)
            })

            logInfo(s"$currentNode: asking new node's")

            try {
              val response = ask(node, "DoYouBelongToAPartition?").asInstanceOf[Option[Long]]
              logInfo(s"$currentNode: new node's response $response")

              currentPartition = Some(Partition(MayFail.unit(currentNode), mayFailedNode :: currentPartition.get.nodes))
              mayFailedNode.bind(node => send(node, YouBelongToThisPartition(currentPartition.get)))
              logInfo(s"updating partition: $currentPartition")
              notifyPartitionMembers()
            } catch {
              case e: Throwable => {
                isLeading = false
              }
            }
            val response = ask(node, "DoYouBelongToAPartition?")

          case _ =>
            logInfo(s"$currentNode: got nothing")
            destroyPartition()
        }

      } while (isLeading && enoughResources() == ReconfigurationlNoSolution())

      if (isLeading) {
        destroyPartition()
      }
    }

    def destroyPartition() {
      currentPartition match {
        case Some(partition) =>
          partition.nodes.foreach(mayFailedNode => mayFailedNode.bind(node =>
            if (node.getId != currentNode.getId) {
              send(node, Exit())
            }
          ))
          currentPartition = None
          lastPartitionUpdate = None
          isLeading = false
          logInfo(s"[$currentNode]: i'm free")
        case None =>
      }
    }

    def notifyPartitionMembers() {
      currentPartition match {
        case Some(partition) =>
          partition.nodes.foreach(mayFailedNode => mayFailedNode.bind(node =>
            if (node.getId != currentNode.getId) {
              ask(node, YouBelongToThisPartition(partition))
            }
          ))
        case None =>
      }
    }

    def exitPartition() {
      currentPartition = None
      lastPartitionUpdate = None
    }

    def main(strings: Array[String]) {
      while (!SimulatorManager.isEndOfInjection) {
        waitFor(1)
      }
    }
  }

  val core: LocalityBasedSchedulerCore = new LocalityBasedSchedulerCore(SimulatorManager.getXHostByName(currentNode.getName).getSGHost, currentNode.getName, 1200);
  core.start()


  def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {
    case YouBelongToThisPartition(remotePartition: Partition) =>
      send(returnCanal, true)
      core.currentPartition match {
        case Some(partition) =>
          remotePartition.leader.bind(otherLeader => partition.leader.bind(myLeader =>
            if (otherLeader.getId < myLeader.getId) {
              send(otherLeader, MergeWithThisPartition(partition))
            } else {
              send(myLeader, MergeWithThisPartition(remotePartition))
            }
          ))
        case None =>
          core.currentPartition = Some(remotePartition)
          core.lastPartitionUpdate = Some(Msg.getClock)
      }

    case "DoYouBelongToAPartition?" =>
      core.currentPartition match {
        case Some(partition) =>
          send(returnCanal, core.currentPartition.get.leader.bind(l => l.getId))
        case None =>
          send(returnCanal, None)
      }

    case Downgrade() =>
      core.isLeading = false

    case MergeWithThisPartition(remotePartition: Partition) =>

      core.currentPartition match {
        case Some(partition) =>
          core.logInfo(s"$currentNode: need to merge $partition with $remotePartition")
          remotePartition.leader.bind(concurrentLeader => send(concurrentLeader, Downgrade()))
          core.currentPartition = Some(Partition(partition.leader, partition.nodes ::: remotePartition.nodes))
          core.lastPartitionUpdate = Some(Msg.getClock)
          core.notifyPartitionMembers()
        case None =>
      }


    case "checkTimeout" if (core.currentPartition != None) =>
      val now = Msg.getClock;
      val duration: Double = now - core.lastPartitionUpdate.get

      if (duration > LocalityBasedScheduler.partitionTimeout) {
        core.logInfo("timeout detected, exit partition")
        core.exitPartition()
      }

    case Exit() =>
      core.currentPartition = None
      core.lastPartitionUpdate = None
      core.logInfo(s"[$currentNode]: i'm free")

    case "updateLastPartitionUpdate" =>
      core.updateLastUpdateTime()

    case "overloadingDetected" =>
      core.currentPartition match {
        case None =>
          core.startIterativeScheduling()
        case _ =>
      }

    case msg =>
  }
}

