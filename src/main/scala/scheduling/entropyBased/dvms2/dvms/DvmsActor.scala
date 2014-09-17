package org.discovery.dvms.dvms

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

import java.util.{Random, UUID}

import org.discovery.dvms.dvms.DvmsProtocol._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.dvms.DvmsModel.DvmsPartititionState._
import org.simgrid.msg.{HostFailureException, Host, Msg}
import org.discovery.dvms.entropy.EntropyActor
import scheduling.entropyBased.dvms2.SGNodeRef
import scheduling.entropyBased.dvms2.SGActor
import configuration.XHost
import simulation.SimulatorManager
import trace.Trace
import org.discovery.DiscoveryModel.model.ReconfigurationModel._
import scheduling.entropyBased.dvms2.dvms.LoggingActor
import scheduling.entropyBased.dvms2.dvms.LoggingProtocol._
import scheduling.entropyBased.dvms2.overlay.SimpleOverlay

object DvmsActor {
  val partitionUpdateTimeout: Double = 3.5
}

class DvmsActor(applicationRef: SGNodeRef) extends SGActor(applicationRef) {

  /* Local states of a DVMS agent */

//  def firstOut: Option[SGNodeRef] = {
//    val filter: List[String] = currentPartition match {
//      case Some(p) => p.nodes.map(n => n.getName)
//      case None => Nil
//    }
//    SimpleOverlay.giveSomeNeighbour(filter)
//  }

  var firstOut: Option[SGNodeRef] = None

  var currentPartition: Option[DvmsPartition] = None
  var lastPartitionUpdateDate: Option[Double] = None
  var lockedForFusion: Boolean = false
  val entropyActor = new EntropyActor(applicationRef)

  implicit def selfSender: SGNodeRef = self

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
    updateLastUpdateTime()
    partition.nodes.filterNot(member => member.getId == self().getId).foreach(member => {
      ask(member, SetCurrentPartition(partition))
    })
  }

  def dissolvePartition(reason: String) {
    logInfo(s"$applicationRef: I dissolve the partition $currentPartition, because <$reason>")

    lockedForFusion = false
    lastPartitionUpdateDate = None
    currentPartition = None

    LoggingActor.write(IsFree(Msg.getClock, s"${applicationRef.getId}"))
  }

  def mergeWithThisPartition(partition: DvmsPartition) {

    logInfo(s"merging $partition with ${currentPartition.get}")
    currentPartition = Some(DvmsPartition(
      currentPartition.get.leader,
      currentPartition.get.initiator,
      currentPartition.get.nodes ::: partition.nodes,
      Growing(), UUID.randomUUID(), 0))

    lastPartitionUpdateDate = Some(Msg.getClock)

    currentPartition.get.nodes.foreach(node => {
      logInfo(s"(a) $applicationRef: sending a new version of the partition ${SetCurrentPartition(currentPartition.get)} to $node")
      send(node, SetCurrentPartition(currentPartition.get))
    })

    val computationResult = computeEntropy()
    computationResult match {
      case solution: ReconfigurationSolution => {
        logInfo(s"(1) the partition $currentPartition is enough to reconfigure")

        applySolution(solution)


        logInfo(s"(a) I decide to dissolve $currentPartition")
        currentPartition.get.nodes.foreach(node => {
          ask(node, DissolvePartition("violation resolved"))
        })
      }
      case ReconfigurationlNoSolution() => {

        logInfo(s"(1a) the partition $currentPartition is not enough to reconfigure," +
          s" I try to find another node for the partition, deadlock? ${currentPartition.get.nodes.contains(firstOut)}")

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
        updateLastUpdateTime()
        true

      case None =>
        currentPartition = Some(remotePartition)
        updateLastUpdateTime()
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

    if (partitionIsStillValid) {

      // the current node is becoming the leader of the incoming ISP
      val newPartition: DvmsPartition = new DvmsPartition(
        applicationRef,
        remotePartition.initiator,
        applicationRef :: remotePartition.nodes,
        Growing(),
        remotePartition.id,
        remotePartition.version + 1
      )

      LoggingActor.write(IsBooked(Msg.getClock, s"${applicationRef.getId}"))

      logInfo(s"$applicationRef: I am becoming the new leader of $newPartition")

      updatePartitionOnAllNodes(newPartition)

      // ask entropy if the new partition is enough to resolve the overload
      val computationResult = computeEntropy()

      computationResult match {
        case solution: ReconfigurationSolution => {
          logInfo("(A) Partition was enough to reconfigure ")

          changeCurrentPartitionState(Finishing())

          updatePartitionOnAllNodes(currentPartition.get)

          // Applying the reconfiguration plan
          applySolution(solution)

          // it was enough: the partition is no more useful
          currentPartition.get.nodes.foreach(node => {
            send(node, DissolvePartition("reconfigurationPlan applied"))
          })
        }
        case ReconfigurationlNoSolution() => {

          firstOut match {
            case Some(existingNode) =>
              logInfo(s"(A) Partition was not enough to reconfigure, forwarding to $existingNode")
              send(existingNode, TransmissionOfAnISP(currentPartition.get))
            case None =>
              logInfo(s"(A) $applicationRef : ${currentPartition.get} was not forwarded to nobody")
          }
        }
      }
    } else {
      logWarning(s"$applicationRef: $remotePartition is no more valid (source: ${remotePartition.initiator})")
    }
  }

  def receivedAnIspWhenBooked(sender: SGNodeRef, remotePartition: DvmsPartition, msg: Object) {

    firstOut match {
      case Some(nextNode) =>
        currentPartition match {
          case Some(p) =>
            (p.state, remotePartition.state) match {
              case (_, Growing()) =>
                if (remotePartition.initiator.getId == self().getId) {
                  changeCurrentPartitionState(Blocked())
                } else {
                  forward(nextNode, sender, msg)
                }

              case (Blocked(), Blocked()) =>
                if (remotePartition.initiator.getId == self().getId) {
                  dissolvePartition("blocked partition went back to its initiator :(")
                } else {
                  mergeWithThisPartition(remotePartition)
                }
              case _ =>
                forward(nextNode, sender, msg)
            }
        }
      case _ =>
        throw new RuntimeException(s"cannot forward $remotePartition to unknown firstOut")
    }
  }

  /* Methods and functions related to fault tolerance */

  def updateLastUpdateTime() {
    lastPartitionUpdateDate = Some(Msg.getClock)
  }

  def checkTimeout() {
    (currentPartition, lastPartitionUpdateDate) match {
      case (Some(p), Some(d)) => {
        val duration: Double = (Msg.getClock - d)
        if (duration > DvmsActor.partitionUpdateTimeout) {
          logInfo(s"$applicationRef: timeout of $duration at partition $currentPartition has been reached: I dissolve everything")
          p.nodes.filterNot(ref => ref.getId == applicationRef.getId).foreach(n => {
            send(n, DissolvePartition("timeout"))
          })
          dissolvePartition("timeout")
        }
      }
      case _ =>
    }
  }

  /* Methods and functions related to reconfiguration plans and migrations */

  def computeEntropy(): ReconfigurationResult = {
    val timeBeforeComputeEntropy = new java.util.Date().getTime()
    val computationResult = entropyActor.computeReconfigurationPlan(currentPartition.get.nodes)
    val timeAfterComputeEntropy = new java.util.Date().getTime()

    LoggingActor.write(ComputingSomeReconfigurationPlan(Msg.getClock, s"${applicationRef.getId}", timeAfterComputeEntropy-timeBeforeComputeEntropy, currentPartition.get.nodes.size, computationResult.toString))
    computationResult
  }

  def applyMigration(m: MakeMigration) {

    println( """/!\ WARNING /!\: Dans DvmsActor, les migrations sont synchrones!""");
    println(s"trying to migrate ${m.vmName} from ${m.from} to ${m.to}");

    val args: Array[String] = new Array[String](3)
    args(0) = m.vmName
    args(1) = m.from
    args(2) = m.to


    var destHost: XHost = null
    var sourceHost: XHost = null
    try {
      sourceHost = SimulatorManager.getXHostByName(args(1))
      destHost = SimulatorManager.getXHostByName(args(2))
    }
    catch {
      case e: Exception => {
        e.printStackTrace
        System.err.println("You are trying to migrate from/to a non existing node")
      }
    }
    if (destHost != null) {

      LoggingActor.write(StartingMigration(Msg.getClock, s"${applicationRef.getId}", m.vmName, m.from, m.to))

      val timeBeforeMigration = Msg.getClock
      sourceHost.migrate(args(0), destHost)
      val timeAfterMigration = Msg.getClock

      LoggingActor.write(FinishingMigration(Msg.getClock, s"${applicationRef.getId}", m.vmName, m.from, m.to, timeAfterMigration - timeBeforeMigration))

      Msg.info("End of migration of VM " + args(0) + " from " + args(1) + " to " + args(2))
      //              CentralizedResolver.decMig
      if (!destHost.isViable) {
        Msg.info("ARTIFICIAL VIOLATION ON " + destHost.getName + "\n")
        Trace.hostSetState(destHost.getName, "PM", "violation-out")
      }
      if (sourceHost.isViable) {
        Msg.info("SOLVED VIOLATION ON " + sourceHost.getName + "\n")
        Trace.hostSetState(sourceHost.getName, "PM", "normal")
      }
    }
  }

  def applySolution(solution: ReconfigurationSolution) {
    def updateTimeout(partition: DvmsPartition) {
      partition.nodes.filterNot(n => n.getId == applicationRef.getId).foreach(node => {
        ask(node, "updateLastPartitionUpdate")
      })
      updateLastUpdateTime()
    }

    println(s"Applying reconfigurationPlan: $solution")

    import scala.collection.JavaConversions._

    val currentPartitionCopy: Option[DvmsPartition] = currentPartition
    currentPartitionCopy match {
      case Some(partition) =>

        var migrationDone: Int = 0
        var migrationCount: Int = 0

        solution.actions.keySet().foreach(key => {
          solution.actions.get(key).foreach(action => {
            action match {
              case m@MakeMigration(from, to, vmName) =>
                println(s"$partition => $m")
                migrationCount += 1
            }
          })
        })

        val threadName = s"${applicationRef.getName}-${new Random().nextInt(10000)}"

        val timeoutProcess = new org.simgrid.msg.Process(applicationRef.getName, s"$threadName-timeout", new Array[String](0)) {
          def main(args: Array[String]) {
            val startingPartitionId: UUID = currentPartition.get.id
            var continue: Boolean = true
            while (continue) {

              updateTimeout(partition)
              waitFor(0.5)

              continue = currentPartition match {
                case Some(partition) if(partition.id == startingPartitionId) => true
                case _ => false
              }
            }
          }
        }

        solution.actions.keySet().foreach(key => {
          solution.actions.get(key).foreach(action => {
            action match {
              case m@MakeMigration(from, to, vmName) =>
                val migrationProcess = new org.simgrid.msg.Process(applicationRef.getName, s"$threadName-migration", new Array[String](0)) {
                  def main(args: Array[String]): Unit = {
                    applyMigration(m)
                    migrationDone += 1
                  }
                }
                migrationProcess.start()
              case _ =>
            }
          })
        })

        timeoutProcess.start()

        while(migrationDone < migrationCount) {
          try {
            org.simgrid.msg.Process.currentProcess.waitFor(1)
          }
          catch {
            case e: HostFailureException => {
              e.printStackTrace
            }
          }
        }

//        timeoutProcess.kill()

        logInfo(s"$applicationRef: reconfiguration plan $solution has been applied, dissolving partition $partition")

        dissolvePartition("Reconfiguration plan has been applied")
//        partition.nodes.foreach(node => {
//          send(node, DissolvePartition("Reconfiguration plan has been applied"))
//        })

      case None =>
        logInfo("cannot apply reconfigurationSolution: current partition is undefined")
    }
  }


  /* Messages reception loop */

  def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {

    case IsThisVersionOfThePartitionStillValid(partition) =>
      logInfo(s"send $sender that partition is still valid ${isThisPartitionStillValid(partition)}")
      send(returnCanal, isThisPartitionStillValid(partition))

    case "updateLastPartitionUpdate" =>
      updateLastUpdateTime()
      send(returnCanal, true)

    case "checkTimeout" =>
      checkTimeout()

    case CanIMergePartitionWithYou(partition, contact) =>
      send(sender, (!lockedForFusion))
      if (!lockedForFusion) {
        lockedForFusion = true
      }

    case DissolvePartition(reason) =>
      dissolvePartition(reason)
      send(returnCanal, true)

    case SetCurrentPartition(partition: DvmsPartition) =>
      val done = updateThePartitionWith(partition)
      send(returnCanal, done)

    case msg@TransmissionOfAnISP(remotePartition) =>
      logInfo(s"received an ISP: $msg @$currentPartition and @$firstOut")
      currentPartition match {
        case Some(p) =>
          receivedAnIspWhenBooked(sender, remotePartition, msg)

        case None =>
          receivedAnIspWhenFree(sender, remotePartition, msg)
      }

    case "overloadingDetected" =>
      currentPartition match {
        case None => {
          logInfo("Dvms has detected a new cpu violation")

          currentPartition = Some(DvmsPartition(
            applicationRef,
            applicationRef,
            List(applicationRef),
            Growing()
          ))

          LoggingActor.write(IsBooked(Msg.getClock, s"${applicationRef.getId}"))

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

    case ThisIsYourNeighbor(node) =>
      firstOut = Some(node)

    case msg => forward(applicationRef, sender, msg)
  }
}


