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

import scala.concurrent.duration._
import java.util.{Date, UUID}

import org.discovery.dvms.dvms.DvmsProtocol._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.dvms.DvmsModel.DvmsPartititionState._
import org.simgrid.msg.Msg
import org.discovery.dvms.entropy.EntropyActor
import scheduling.entropyBased.dvms2.SGNodeRef
import scheduling.entropyBased.dvms2.SGActor
import configuration.XHost
import simulation.SimulatorManager
import org.simgrid.trace.Trace

//import org.discovery.dvms.entropy.EntropyProtocol.{EntropyComputeReconfigurePlan}
import org.discovery.DiscoveryModel.model.ReconfigurationModel._

object DvmsActor {
  val partitionUpdateTimeout: FiniteDuration = 3500 milliseconds


}


class DvmsActor(applicationRef: SGNodeRef) extends SGActor(applicationRef) {

  // by default, a node is in a ring containing only it self
  //  var nextDvmsNode: SGNodeRef = applicationRef

  // Variables that are specific to a node member of a partition
  var firstOut: Option[SGNodeRef] = None

  var currentPartition: Option[DvmsPartition] = None

  // Variables used for resiliency
  var lastPartitionUpdateDate: Option[Date] = None

  var lockedForFusion: Boolean = false

  //  val self: SGNodeRef = applicationRef

  def logInfo(msg: String) {
    Msg.info(s"$msg")
    //    println(s"$msg")
  }

  def logWarning(msg: String) {
    Msg.info(s"$msg")
    //    println(s"$msg")
  }

  val entropyActor = new EntropyActor(applicationRef)

  implicit def selfSender: SGNodeRef = self

  def mergeWithThisPartition(partition: DvmsPartition) {

    logInfo(s"merging $partition with ${currentPartition.get}")
    currentPartition = Some(DvmsPartition(
      currentPartition.get.leader,
      currentPartition.get.initiator,
      currentPartition.get.nodes ::: partition.nodes,
      Growing(), UUID.randomUUID()))

    lastPartitionUpdateDate = Some(new Date())

    currentPartition.get.nodes.foreach(node => {
      logInfo(s"(a) $applicationRef: sending a new version of the partition ${IAmTheNewLeader(currentPartition.get)} to $node")
      send(node, IAmTheNewLeader(currentPartition.get))
    })

    val computationResult = computeEntropy()
    computationResult match{
      case solution: ReconfigurationSolution => {
        logInfo(s"(1) the partition $currentPartition is enough to reconfigure")

        applySolution(solution)


        logInfo(s"(a) I decide to dissolve $currentPartition")
        currentPartition.get.nodes.foreach(node => {
          send(node, DissolvePartition("violation resolved"))
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

        currentPartition = Some(DvmsPartition(
          partition.leader,
          partition.initiator,
          partition.nodes,
          newState,
          partition.id
        ))

        lastPartitionUpdateDate = Some(new Date())

      case None =>
    }
  }

  def remoteNodeFailureDetected(node: SGNodeRef) {
    currentPartition match {
      case Some(p) => {
        if (p.nodes.contains(node)) {
          node match {
            // the initiator of the partition has crashed
            case node: SGNodeRef if (node isEqualTo p.initiator) => {

              logInfo(s"$applicationRef: The initiator ($node) has crashed, I am becoming the new leader of $currentPartition")

              // the partition will be dissolved
              p.nodes.filterNot(n => n isEqualTo node).foreach(n => {
                send(n, DissolvePartition("initiator crashed"))
              })
            }

            // the leader or a normal node of the partition has crashed
            case node: SGNodeRef => {

              // creation of a new partition without the crashed node
              val newPartition: DvmsPartition = new DvmsPartition(
                applicationRef,
                p.initiator,
                p.nodes.filterNot(n => n isEqualTo node),
                p.state,
                UUID.randomUUID()
              )

              currentPartition = Some(newPartition)
              //              firstOut = Some(nextDvmsNode)

              lastPartitionUpdateDate = Some(new Date())

              logInfo(s"$applicationRef: A node crashed ($node), I am becoming the new leader of $currentPartition")

              newPartition.nodes.foreach(node => {
                send(node, IAmTheNewLeader(newPartition))
              })
            }
          }
        }
      }
      case None =>
    }

  }

  def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {

    case IsThisVersionOfThePartitionStillValid(partition) => {
      currentPartition match {
        case Some(p) =>
          send(sender, partition.id.equals(currentPartition.get.id))
        case None =>
          send(sender, false)
      }
    }

    case FailureDetected(node) => {
      remoteNodeFailureDetected(node)
    }


    case CheckTimeout() => {

      //         logInfo(s"$applicationRef: check if we have reach the timeout of partition")

      //      logInfo("checkTimeout")
      //      printDetails()

      (currentPartition, lastPartitionUpdateDate) match {
        case (Some(p), Some(d)) => {

          val now: Date = new Date()
          val duration: Duration = (now.getTime - d.getTime) milliseconds

          if (duration > DvmsActor.partitionUpdateTimeout) {

            logInfo(s"$applicationRef: timeout of partition has been reached: I dissolve everything")

            p.nodes.foreach(n => {
              send(n, DissolvePartition("timeout"))
            })
          }

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

    case DissolvePartition(reason) => {

      currentPartition match {
        case Some(p) =>
          logInfo(s"$applicationRef: I dissolve the partition $p, because <$reason>")
        case None =>
          logInfo(s"$applicationRef: I dissolve the partition None, because <$reason>")
      }


      //      firstOut = None
      currentPartition = None
      lockedForFusion = false
      lastPartitionUpdateDate = None

      // Alert LogginActor that the current node is free
      //      applicationRef.ref ! IsFree(ExperimentConfiguration.getCurrentTime())
    }

    case IAmTheNewLeader(partition) => {

      logInfo(s"$applicationRef: ${partition.leader} is the new leader of $partition")

      val outdatedUpdate: Boolean = (currentPartition, partition.state) match {
        case (None, Finishing()) => true
        case _ => false
      }

      if(!outdatedUpdate) {
        currentPartition = Some(partition)
        lastPartitionUpdateDate = Some(new Date())

        lockedForFusion = false

        //        firstOut match {
        //          case None => firstOut = Some(firstOutOfTheLeader)
        //          case Some(node) => {
        //            if (firstOut.get.location isEqualTo partition.leader.location) {
        //              firstOut = Some(firstOutOfTheLeader)
        //            }
        //          }
        //        }
      }
    }

    case ChangeTheStateOfThePartition(newState) => {
      changeCurrentPartitionState(newState)
    }

    case msg@TransmissionOfAnISP(partition) => {

      logInfo(s"received an ISP: $msg @$currentPartition and @$firstOut")
      //      printDetails()

      currentPartition match {
        case Some(p) => p match {
          // the ISP went back to it's initiator for the first time
          case _ if ((partition.initiator isEqualTo p.initiator)
            && (partition.state isEqualTo Growing())) => {

            logInfo(s"$applicationRef: the partition $partition went back to it's initiator" +
              s" with a Growing state: it becomes blocked :s")


            changeCurrentPartitionState(Blocked())

            // the state of the current partition become Blocked()
            p.nodes.foreach(node => {
              send(node, ChangeTheStateOfThePartition(Blocked()))
            })


            firstOut match {
              case Some(existingNode) =>
                logInfo(s"(X) $applicationRef transmitting a new ISP ${currentPartition.get} to neighbour: $existingNode")
                send(existingNode, TransmissionOfAnISP(currentPartition.get))
              case None =>
                logInfo(s"(X) $applicationRef transmitting a new ISP ${currentPartition.get} to nobody")
            }



          }
          // the ISP went back to it's initiator for the second time
          case _ if ((partition.initiator isEqualTo p.initiator)
            && (partition.state isEqualTo Blocked())) => {

            logInfo(s"$applicationRef: the partition $partition went back to it's initiator" +
              s" with a Blocked state: it dissolve it :(")
            // the currentPartition should be dissolved
            p.nodes.foreach(node => {
              send(node, DissolvePartition("back to initiator with a blocked state"))
            })

          }
          // the incoming ISP is different from the current ISP and the current state is not Blocked
          case _ if ((partition.initiator isDifferentFrom p.initiator)
            && (p.state isEqualTo Growing())) => {

            // I forward the partition to the current firstOut
            firstOut match {
              case Some(existingNode) =>
                logInfo(s"$applicationRef: forwarding $msg to $firstOut")
                forward(firstOut.get, sender, msg)
              case None =>
                logInfo(s"$applicationRef: cannot forward to firstOut")
            }


          }
          // the incoming ISP is different from the current ISP and the current state is Blocked
          //   ==> we may merge!
          case _ if ((partition.initiator isDifferentFrom p.initiator)
            && (p.state isEqualTo Blocked())) => {

            partition.state match {
              case Blocked() => {

                if (partition.initiator isSuperiorThan p.initiator) {
                  logInfo(s"$applicationRef: may merge $p with $partition")


                  lockedForFusion = true
                  val willMerge: Boolean = ask(sender, CanIMergePartitionWithYou(p, applicationRef)).asInstanceOf[Boolean]

                  logInfo(s"$applicationRef got a result $willMerge")

                  willMerge match {
                    case true => {
                      lockedForFusion = true

                      logInfo(s"$applicationRef is effectively merging partition $p with $partition")

                      mergeWithThisPartition(partition)
                    }
                    case false =>
                  }
                } else {

                  firstOut match {
                    case Some(existingNode) =>
                      // the order between nodes is not respected, the ISP should be forwarded
                      logInfo(s"$applicationRef: order between nodes is not respected, I forward $partition to $existingNode")
                      forward(existingNode, sender, msg)
                    case None =>
                      // the order between nodes is not respected, the ISP should be forwarded
                      logInfo(s"bug:$applicationRef: cannot forward $partition to $firstOut")
                  }

                }

              }

              case Finishing() =>  {
                firstOut match {
                  case Some(existingNode) =>
                    logInfo(s"$applicationRef: forwarding $msg to $firstOut")
                    forward(existingNode, sender, msg)
                  case None =>
                    logInfo(s"$applicationRef: cannot forward to firstOut")
                }
              }

              case Growing() => {

                firstOut match {
                  case Some(existingNode) =>
                    logInfo(s"$applicationRef: forwarding $msg to $firstOut")
                    forward(existingNode, sender, msg)
                  case None =>
                    logInfo(s"$applicationRef: cannot forward to firstOut")
                }

              }
            }
          }
          // other case... (if so)
          case _ => {
            firstOut match {
              case Some(existingNode) =>
                logInfo(s"$applicationRef: forwarding $msg to $firstOut (forward-bis)")
                forward(existingNode, sender, msg)
              case None =>
                logInfo(s"$applicationRef: cannot forward to firstOut (forward-bis)")
            }
          }
        }

        case None => {

          var partitionIsStillValid: Boolean = true

          if (partition.state isEqualTo Blocked()) {
            try {

              // TODO: there was a mistake reported here!
              partitionIsStillValid = ask(partition.initiator, IsThisVersionOfThePartitionStillValid(partition)).asInstanceOf[Boolean]

            } catch {
              case e: Throwable => {
                logInfo(s"Partition $partition is no more valid (Exception")
                e.printStackTrace()
                partitionIsStillValid = false
              }
            }

          }

          if (partitionIsStillValid) {

            // the current node is becoming the leader of the incoming ISP
            logInfo(s"$applicationRef: I am becoming the new leader of $partition")

            val newPartition: DvmsPartition = new DvmsPartition(
              applicationRef,
              partition.initiator,
              applicationRef :: partition.nodes,
              Growing(),
              UUID.randomUUID()
            )

            currentPartition = Some(newPartition)
            //            firstOut = Some(nextDvmsNode)
            lastPartitionUpdateDate = Some(new Date())

            // Alert LogginActor that the current node is booked in a partition
            //            applicationRef.ref ! IsBooked(ExperimentConfiguration.getCurrentTime())

            partition.nodes.foreach(node => {
              logInfo(s"$applicationRef: sending the $newPartition to $node")
              send(node, IAmTheNewLeader(newPartition))
            })

            lastPartitionUpdateDate = Some(new Date())

            // ask entropy if the new partition is enough to resolve the overload

            val computationResult = computeEntropy()

            computationResult match {
              case solution: ReconfigurationSolution => {
                logInfo("(A) Partition was enough to reconfigure ")


                val newPartition: DvmsPartition = new DvmsPartition(
                  applicationRef,
                  partition.initiator,
                  applicationRef :: partition.nodes,
                  Finishing(),
                  UUID.randomUUID()
                )

                currentPartition = Some(newPartition)
                //                firstOut = Some(nextDvmsNode)


                // Alert LogginActor that the current node is booked in a partition
                //                applicationRef.ref ! IsBooked(ExperimentConfiguration.getCurrentTime())

                partition.nodes.filter(n => n.isDifferentFrom(applicationRef)).foreach(node => {
                  logInfo(s"$applicationRef: sending the $newPartition to $node")
                  //                  node.ref ! IAmTheNewLeader(newPartition)
                  send(node, IAmTheNewLeader(newPartition))
                })

                lastPartitionUpdateDate = Some(new Date())

                // Applying the reconfiguration plan
                applySolution(solution)

                // it was enough: the partition is no more useful
                currentPartition.get.nodes.foreach(node => {
                  //                  node.ref ! DissolvePartition("violation resolved")
                  send(node, DissolvePartition("violation resolved"))
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
            logWarning(s"$applicationRef: $partition is no more valid (source: ${partition.initiator})")
          }
        }
      }
    }

    case "faultDetected" => {

      currentPartition match {
        case None => {
          logInfo("Dvms has detected a new cpu violation")
          //              printDetails()

          //          firstOut = Some(nextDvmsNode)

          currentPartition = Some(DvmsPartition(
            applicationRef,
            applicationRef,
            List(applicationRef),
            Growing(),
            UUID.randomUUID()
          ))

          lastPartitionUpdateDate = Some(new Date())

          // Alert LogginActor that the current node is booked in a partition
          //        applicationRef.ref ! IsBooked(ExperimentConfiguration.getCurrentTime())

          firstOut match {
            case Some(existingNode) =>
              logInfo(s"$applicationRef transmitting a new ISP ${currentPartition.get} to neighbour: $existingNode")
              send(existingNode, TransmissionOfAnISP(currentPartition.get))
            case None =>
              logInfo(s"$applicationRef transmitting a new ISP ${currentPartition.get} to nobody")
          }

        }
        case _ =>
        //              println(s"violation detected: this is my Partition [$currentPartition]")
      }

    }

    case ThisIsYourNeighbor(node) => {
      //      logInfo(s"my neighbor has changed: $node")
      firstOut = Some(node)
    }

    case YouMayNeedToUpdateYourFirstOut(oldNeighbor: Option[SGNodeRef], newNeighbor: SGNodeRef) => {

      //      (firstOut, oldNeighbor) match {
      //        case (Some(fo), Some(n)) if (fo.location isEqualTo n.location) => firstOut = Some(newNeighbor)
      //        case _ =>
      //      }
    }

    case msg => forward(applicationRef, sender, msg)
  }


  def computeEntropy(): ReconfigurationResult = {

    logInfo("computeEntropy (1)")


    // TODO: reimplement call to entropy actor
    println("Please reimplement the call to entropy Actor")
    val computationResult = entropyActor.computeReconfigurationPlan(currentPartition.get.nodes)

    logInfo("computeEntropy (2)")

    computationResult
  }

  def applyMigration(m: MakeMigration) {
    // TODO: appliquer les migrations ici
    println("""/!\ WARNING /!\: Dans DvmsActor, les migrations sont synchrones!""");

    val args: Array[String] = new Array[String](3)
    args(0) = m.vmName
    args(1) = m.from
    args(2) = m.to

    //    new org.simgrid.msg.Process(Host.getByName(m.from), "Migrate-" + new Random().nextDouble, args) {
    //      def main(args: Array[String]) {
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

      sourceHost.migrate(args(0), destHost)

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

    //      }
    //    }.start
  }

  def applySolution(solution: ReconfigurationSolution) {

    import scala.collection.JavaConversions._

    currentPartition match {
      case Some(partition) =>

        var continueToUpdatePartition: Boolean = true


        solution.actions.keySet().foreach(key => {
          solution.actions.get(key).foreach(action => {


            action match {
              case m@MakeMigration(from, to, vmName) =>
                applyMigration(m)
              case _ =>
            }
          })
        })



        continueToUpdatePartition = false

        partition.nodes.foreach(node => {
          logInfo(s"$applicationRef: reconfiguration plan has been applied, dissolving partition $partition")
          send(node, DissolvePartition("Reconfiguration plan has been applied"))
        })


      case None =>
        logInfo("cannot apply reconfigurationSolution: current partition is undefined")
    }
  }
}


