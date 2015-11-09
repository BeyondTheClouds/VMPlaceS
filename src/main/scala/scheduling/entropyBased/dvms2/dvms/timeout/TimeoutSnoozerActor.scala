package scheduling.entropyBased.dvms2.dvms.timeout

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

import scheduling.dvms2.{SGActor, SGNodeRef}
import scheduling.entropyBased.dvms2.dvms.timeout.TimeoutProtocol._
import scheduling.entropyBased.dvms2.dvms.dvms2.DvmsModel.DvmsPartition
import org.simgrid.msg.Host
import org.simgrid.msg.Process
import simulation.SimulatorManager
import scheduling.entropyBased.dvms2.dvms.dvms2.DvmsProtocol.SnoozeTimeout

class TimeoutSnoozerActor(applicationRef: SGNodeRef, host: Host) extends SGActor(applicationRef) {

  var enableSnoozing = false
  var currentPartition: Option[List[SGNodeRef]] = None

  var continue = false
  var core: Option[Process] = None

  def spawnNewProcess() {
    continue = true
    val newcore = new Process(host, host.getName + "-timeoutprocess-core", new Array[String](0)) {
      def main(args: Array[String]) {
        while (!SimulatorManager.isEndOfInjection && continue) {
          println(s"looping with $enableSnoozing and $currentPartition")
          if (enableSnoozing) {
            currentPartition match {
              case Some(p) => p.foreach(n => send(n, SnoozeTimeout()))
              case _ =>
            }
          }
          waitFor(2)
        }
        println(s"terminating timeoutprocess-core of ${applicationRef}")
      }
    }
    core = Some(newcore)
    newcore.start()
  }

  override def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {

    case EnableTimeoutSnoozing() =>
      enableSnoozing = true
      core match {
        case None =>
          spawnNewProcess()
        case _ =>
      }
      send(returnCanal, true)

    case DisableTimeoutSnoozing() =>
      enableSnoozing = false
      core match {
        case Some(p) =>
          continue = false
          core = None
        case _ =>
      }
      send(returnCanal, true)

    case WorkOnThisPartition(p: List[SGNodeRef]) =>
      currentPartition = Some(p)
      core match {
        case None =>
          spawnNewProcess()
        case _ =>
      }

    case msg =>
      println(s"unknown message: $msg")
  }
}
