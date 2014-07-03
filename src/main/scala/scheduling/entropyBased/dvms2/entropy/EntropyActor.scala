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

// TODO à fusionner avec dvms_scala/EntropyActor de SimgridInjector

import scheduling.entropyBased.dvms2.SGActor
import entropy.plan.choco.ChocoCustomRP
import entropy.plan.durationEvaluator.MockDurationEvaluator
import org.discovery.dvms.entropy.EntropyProtocol.MigrateVirtualMachine
import org.discovery.DiscoveryModel.model.ReconfigurationModel.{ReconfigurationlNoSolution, ReconfigurationResult}
import entropy.configuration.{SimpleVirtualMachine, SimpleNode, SimpleConfiguration, Configuration}
import org.discovery.dvms.dvms.DvmsModel.{ComputerSpecification, VirtualMachine, PhysicalNode}
import simulation.{SimulatorManager, Main}
import configuration._
import scheduling.entropyBased.dvms2.{EntropyService, SGNodeRef}

//import org.discovery.EntropyService
import scala.collection.JavaConversions._

class EntropyActor(applicationRef: SGNodeRef) extends AbstractEntropyActor(applicationRef) {

  val planner: ChocoCustomRP = new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));
  planner.setTimeLimit(3);

  //   def computeReconfigurationPlan(nodes: List[NodeRef]): Boolean = {

  def computeReconfigurationPlan(nodes: List[SGNodeRef]): ReconfigurationResult = {

    val initialConfiguration: Configuration = new SimpleConfiguration();

    val physicalNodesWithVmsConsumption: List[PhysicalNode] = nodes.map(n => {
      val host = SimulatorManager.getXHostByName(n.getName)
      val vms = host.getRunnings

      var vmsAsScalaArray: List[entropy.configuration.VirtualMachine] = List()
      try {
        vmsAsScalaArray
        = vms.map(vm =>
          new SimpleVirtualMachine(vm.getName, vm.getCoreNumber.toInt, 100, vm.getMemSize, vm.getCPUDemand.toInt, 0)
        ).toList
      } catch {
        case e: Exception =>
          e.printStackTrace()
          vmsAsScalaArray = List()
      }
      PhysicalNode(
        new SGNodeRef(n.getName, n.getId),
        vmsAsScalaArray.map(vm =>
          VirtualMachine(
            vm.getName,
            vm.getCPUDemand,
            ComputerSpecification(vm.getNbOfCPUs, vm.getMemoryDemand, vm.getCPUDemand))),
        "",
        ComputerSpecification(host.getNbCores, host.getMemSize, host.getCPUCapacity)
      )
    })


    physicalNodesWithVmsConsumption.foreach(physicalNodeWithVmsConsumption => {

      val entropyNode = new SimpleNode(physicalNodeWithVmsConsumption.ref.toString,
        physicalNodeWithVmsConsumption.specs.numberOfCPU,
        physicalNodeWithVmsConsumption.specs.coreCapacity,
        physicalNodeWithVmsConsumption.specs.ramCapacity);
      initialConfiguration.addOnline(entropyNode);

      physicalNodeWithVmsConsumption.machines.foreach(vm => {
        val entropyVm = new SimpleVirtualMachine(vm.name,
          vm.specs.numberOfCPU,
          0,
          vm.specs.ramCapacity,
          vm.specs.coreCapacity,
          vm.specs.ramCapacity);
        initialConfiguration.setRunOn(entropyVm, entropyNode);
      })
    })

    EntropyService.computeReconfigurationPlan(initialConfiguration, physicalNodesWithVmsConsumption)
    //    println("""/!\ UNIMPLEMENTED /!\: EntropyActor.computeReconfigurationPlan()""");
    //    ReconfigurationlNoSolution()
  }


  override def receive(message: Object, sender: SGNodeRef, returnCanal: SGNodeRef) = message match {

    case MigrateVirtualMachine(vmName, destination) => {
      // Todo: reimplement this
      println( """/!\ UNIMPLEMENTED /!\: EntropyActor.receive: MigrateVirtualMachine(vmName, destination)""");

    }

    case msg => super.receive(msg, sender, returnCanal)
  }
}
