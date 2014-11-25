//package scheduling.entropyBased.dvms2.dvms.dvms3
//
///* ============================================================
// * Discovery Project - DVMS
// * http://beyondtheclouds.github.io/
// * ============================================================
// * Copyright 2013 Discovery Project.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// * ============================================================ */
//
//import scheduling.entropyBased.dvms2.{DVMSProcess, SGActor, SGNodeRef}
//import org.simgrid.msg.Msg
//import scheduling.entropyBased.dvms2.dvms.dvms2.LoggingProtocol
//import LoggingProtocol._
//import scheduling.entropyBased.dvms2.overlay.SimpleOverlay
//import java.util
//import configuration.XHost
//import simulation.SimulatorManager
//import scheduling.entropyBased.entropy2.Entropy2RP
//import entropy.configuration.Configuration
//import java.util.Random
//import scheduling.entropyBased.dvms2.dvms.dvms3.LocalityBasedSchedulerProtocol._
//
//trait DvmsMessage
//
//object LocalityBasedSchedulerProtocol {
//
//  case class YouBelongToThisPartition(partition: Partition) extends DvmsMessage
//
//  case class MergeWithThisPartition(partition: Partition) extends DvmsMessage
//
//  case class Downgrade() extends DvmsMessage
//
//  case class PartitionChanged(partition: Partition) extends DvmsMessage
//
//  case class Exit() extends DvmsMessage
//
//}
//
//case class Partition(leader: MayFail[SGNodeRef], nodes: List[MayFail[SGNodeRef]])
//
//object LocalityBasedScheduler {
//  val partitionTimeout: Double = 3.5
//}
//
//class LocalityBasedScheduler(currentNode: SGNodeRef, parentProcess: DVMSProcess) extends SGActor(currentNode) {
//
//  class LocalityBasedSchedulerCore extends org.simgrid.msg.Process {
//
//    implicit val timeout = 2.5
//    var isLeading = false
//    var currentPartition: Option[Partition] = None
//    var lastPartitionUpdate: Option[Double] = None
//
//    def logInfo(msg: String) {
//      Msg.info(s"$msg")
//    }
//
//    def updateLastUpdateTime() {
//      lastPartitionUpdate = Some(Msg.getClock)
//    }
//
//    def enoughResources(partition: Partition): Boolean = {
//
//      val hostsToCheck: util.LinkedList[XHost] = new util.LinkedList[XHost]
//      val nodesName = partition.nodes.map(n => n.bind(ref => s"${ref.getName}")).flatten
//      for (node <- nodesName) {
//        hostsToCheck.add(SimulatorManager.getXHostByName(node))
//      }
//
//      def updateTimeout(partition: Partition) {
//        val localId: Long = currentNode.getId
//        partition.nodes.map(n => n.bind(n => {
//          if (n.getId != localId) {
//            send(n, "updateLastPartitionUpdate")
//          }
//        }))
//        updateLastUpdateTime()
//      }
//
//
//      val threadName = s"${currentNode.getName}-${new Random().nextInt(10000)}"
//      val timeoutProcess = new org.simgrid.msg.Process(currentNode.getName, s"$threadName-timeout", new Array[String](0)) {
//        def main(args: Array[String]) {
//          while (true) {
//            updateTimeout(currentPartition.get)
//            waitFor(1.5)
//          }
//        }
//      }
//
//      val before = Msg.getClock
//      timeoutProcess.start()
//      val scheduler: Entropy2RP = new Entropy2RP(Entropy2RP.ExtractConfiguration(hostsToCheck).asInstanceOf[Configuration])
//      val entropyRes: Entropy2RP#Entropy2RPRes = scheduler.checkAndReconfigure(hostsToCheck)
//      timeoutProcess.kill()
//
//      val duration = Msg.getClock - before
//      logInfo(s"duration: $duration and result: ${entropyRes.getRes}")
//      entropyRes.getRes == 0
//    }
//
//    def startIterativeScheduling() {
//
//      val threadName = s"${currentNode.getName}-${new Random().nextInt(10000)}"
//      val schedulingProcess = new org.simgrid.msg.Process(currentNode.getName, s"$threadName-timeout", new Array[String](0)) {
//        def main(args: Array[String]) {
//
//
//          isLeading = true
//          currentPartition = Some(Partition(MayFail.unit(currentNode), List(MayFail.unit(currentNode))))
//          lastPartitionUpdate = Some(Msg.getClock)
//
//          // Alert LogginActor that a violation has been detected
//          send(currentNode, ViolationDetected(Msg.getClock, s"${currentNode}"))
//
//          logInfo(s"$currentNode: starting ISP")
//          send(currentNode, IsBooked(Msg.getClock, s"${currentNode}"))
//
//          do {
//            logInfo(s"$currentNode: asking for a Node")
//            val filter = currentPartition.get.nodes.map(n => n.bind(ref => s"${ref.getName}")).flatten
//            SimpleOverlay.giveSomeNeighbour(filter) match {
//
//              case Some(node: SGNodeRef) =>
//                logInfo(s"$currentNode: got $node")
//                val mayFailedNode = MayFail.unit(node)
//                mayFailedNode.watch(failedNode => {
//                  logInfo("removing failed node")
//                  currentPartition = Some(Partition(MayFail.unit(currentNode),
//                    currentPartition.get.nodes.filter(mayFailedNode => failedNode != mayFailedNode)))
//                  lastPartitionUpdate = Some(Msg.getClock)
//                })
//
//                logInfo(s"$currentNode: asking new node's")
//
//                try {
//                  val response = ask(node, "DoYouBelongToAPartition?").asInstanceOf[Option[Long]]
//                  logInfo(s"$currentNode: new node's response $response")
//
//                  currentPartition = Some(Partition(MayFail.unit(currentNode), mayFailedNode :: currentPartition.get.nodes))
//                  mayFailedNode.bind(node => send(node, YouBelongToThisPartition(currentPartition.get)))
//                  logInfo(s"updating partition: $currentPartition")
//                  notifyPartitionMembers()
//                } catch {
//                  case e: Throwable => {
//                    isLeading = false
//                  }
//                }
//                val response = ask(node, "DoYouBelongToAPartition?")
//
//              case _ =>
//                destroyPartition()
//            }
//
//          } while (isLeading && !enoughResources(currentPartition.get))
//
//          if (isLeading) {
//            destroyPartition()
//          }
//
//        }
//      }
//      schedulingProcess.start()
//    }
//
//
//    def destroyPartition() {
//      currentPartition match {
//        case Some(partition) =>
//          partition.nodes.foreach(mayFailedNode => mayFailedNode.bind(node =>
//            if (node.getId != currentNode.getId) {
//              send(node, Exit())
//            }
//          ))
//          currentPartition = None
//          lastPartitionUpdate = None
//          isLeading = false
//          logInfo(s"[$currentNode]: i'm free")
//        case None =>
//      }
//    }
//
//    def notifyPartitionMembers() {
//      currentPartition match {
//        case Some(partition) =>
//          partition.nodes.foreach(mayFailedNode => mayFailedNode.bind(node =>
//            if (node.getId != currentNode.getId) {
//              send(node, PartitionChanged(partition))
//            }
//          ))
//        case None =>
//      }
//    }
//
//    def exitPartition() {
//      currentPartition = None
//      lastPartitionUpdate = None
//    }
//
//    def main(strings: Array[String]) {
//      while (!SimulatorManager.isEndOfInjection) {
//        waitFor(1)
//      }
//    }
//  }
//
//  def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {
//    case YouBelongToThisPartition(remotePartition: Partition) =>
//      currentPartition match {
//        case Some(partition) =>
//          remotePartition.leader.bind(otherLeader => partition.leader.bind(myLeader =>
//            if (otherLeader.getId < myLeader.getId) {
//              send(otherLeader, MergeWithThisPartition(partition))
//            } else {
//              send(myLeader, MergeWithThisPartition(remotePartition))
//            }
//          ))
//        case None =>
//          currentPartition = Some(remotePartition)
//          lastPartitionUpdate = Some(Msg.getClock)
//      }
//
//    case "DoYouBelongToAPartition?" =>
//      currentPartition match {
//        case Some(partition) =>
//          send(sender, currentPartition.get.leader.bind(l => l.getId))
//        case None =>
//          send(sender, None)
//      }
//
//    case Downgrade() =>
//      isLeading = false
//
//    case MergeWithThisPartition(remotePartition: Partition) =>
//
//      currentPartition match {
//        case Some(partition) =>
//          logInfo(s"$currentNode: need to merge $partition with $remotePartition")
//          remotePartition.leader.bind(concurrentLeader => send(concurrentLeader, Downgrade()))
//          currentPartition = Some(Partition(partition.leader, partition.nodes ::: remotePartition.nodes))
//          lastPartitionUpdate = Some(Msg.getClock)
//          notifyPartitionMembers()
//        case None =>
//      }
//
//
//    case "checkTimeout" if (currentPartition != None) =>
//      val now = Msg.getClock;
//      val duration: Double = now - lastPartitionUpdate.get
//
//      if (duration > LocalityBasedScheduler.partitionTimeout) {
//        logInfo("timeout detected, exit partition")
//        exitPartition()
//      }
//
//    case Exit() =>
//      currentPartition = None
//      lastPartitionUpdate = None
//      logInfo(s"[$currentNode]: i'm free")
//
//    case "updateLastPartitionUpdate" =>
//      updateLastUpdateTime()
//
//    case "overloadingDetected" =>
//      currentPartition match {
//        case None =>
//          startIterativeScheduling()
//        case _ =>
//      }
//
//    case msg =>
//  }
//}
//
