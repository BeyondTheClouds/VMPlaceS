package org.discovery.dvms.entropy

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

import java.lang.reflect.Constructor

import entropy.plan.choco.ChocoCustomRP
import entropy.plan.durationEvaluator.MockDurationEvaluator
import org.discovery.dvms.entropy.EntropyProtocol.ComputeAndApplyPlan
import org.discovery.DiscoveryModel.model.ReconfigurationModel.{ReconfigurationAction, ReconfigurationSolution, ReconfigurationlNoSolution, ReconfigurationResult}
import scheduling.{Scheduler, SchedulerRes}
import scheduling.distributed.dvms2.{SGActor, SGNodeRef}
import java.util
import configuration.{SimulatorProperties, XHost}
import simulation.SimulatorManager

class EntropyActor(applicationRef: SGNodeRef) extends SGActor(applicationRef) {

  val planner: ChocoCustomRP = new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4))
  planner.setTimeLimit(3)


  def computeReconfigurationPlan(nodes: List[SGNodeRef]): ReconfigurationResult = {
    val hostsToCheck: util.LinkedList[XHost] = new util.LinkedList[XHost]
    for (node <- nodes) {
      hostsToCheck.add(SimulatorManager.getXHostByName(node.getName))
    }

    val schedulerClass: Class[_] = Class.forName(SimulatorProperties.getImplementation)
    val schedulerConstructor: Constructor[_] = schedulerClass.getConstructor(classOf[util.Collection[XHost]])

    val scheduler: Scheduler = schedulerConstructor.newInstance(hostsToCheck).asInstanceOf[Scheduler]
    val schedulerRes: SchedulerRes = scheduler.checkAndReconfigure(hostsToCheck)
    schedulerRes.getRes match {
      case 0 => ReconfigurationSolution(new java.util.HashMap[String, java.util.List[ReconfigurationAction]]())
      case _ => ReconfigurationlNoSolution()
      // TODO How did you manage the three cases ?
    }
  }


  override def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {

    case ComputeAndApplyPlan(nodes) => {
      val result = computeReconfigurationPlan(nodes)
      send(returnCanal, result)
    }

    case msg =>
      println(s"unknown message: $msg")
  }
}
