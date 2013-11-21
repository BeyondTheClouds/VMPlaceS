package entropy

import plan.{PlanException, TimedReconfigurationPlan}
import scheduling.dvms2.{EntropyService, SGNodeRef}
import entropy.plan.choco.ChocoCustomRP
import entropy.plan.durationEvaluator.MockDurationEvaluator
import dvms.scheduling.ComputingState
import configuration._
import dvms_scala.{ComputerSpecification, VirtualMachine, PhysicalNode}
import simulation.Main

import entropy.vjob.DefaultVJob
import entropy.vjob.VJob
import dvms_scala.PhysicalNode
import dvms_scala.ComputerSpecification
import dvms_scala.VirtualMachine

//import entropy.configuration.{SimpleManagedElementSet, SimpleVirtualMachine, SimpleNode}
//import entropy.vjob.{DefaultVJob, VJob}
//import entropy.plan.{PlanException, TimedReconfigurationPlan}
//import dvms_scala.PhysicalNode

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/18/13
 * Time: 3:24 PM
 * To change this template use File | Settings | File Templates.
 */

class ResultOfComputation(informations: ComputationInformation)
case class PositiveResultOfComputation(ComputationPlan: TimedReconfigurationPlan, information: ComputationInformation) extends ResultOfComputation(information)
case class NegativeResultOfComputation(information: ComputationInformation) extends ResultOfComputation(information)

case class ComputationInformation(cost: Int, migrationCount: Int, graphDepth: Int, timeToCompute: Long)

class EntropyActor(applicationRef: SGNodeRef) extends AbstractEntropyActor(applicationRef) {



    def computeReconfigurationPlan(nodes: List[SGNodeRef]): ResultOfComputation = {

        val initialConfiguration: Configuration = new SimpleConfiguration();

        val physicalNodesWithVmsConsumption: List[PhysicalNode] = nodes.map(n => {
            val entropyNode: Node = Main.getCurrentConfig.getAllNodes.get(n.getName)



            val vms = Main.getCurrentConfig().getRunnings(entropyNode)
            val vmsAsScalaArray:List[entropy.configuration.VirtualMachine]
            = vms.toArray().map(_.asInstanceOf[entropy.configuration.VirtualMachine]).toList

            PhysicalNode(new SGNodeRef(n.getName, n.getId), vmsAsScalaArray.map(vm =>
                VirtualMachine(vm.getName, vm.getCPUDemand, ComputerSpecification(vm.getNbOfCPUs, vm.getMemoryDemand, vm.getCPUDemand))),
                ComputerSpecification(entropyNode.getNbOfCPUs, entropyNode.getMemoryCapacity, entropyNode.getCPUCapacity)
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

        EntropyService.computeReconfigurationPlan(initialConfiguration)
    }

    def applyReconfigurationPlan(plan: TimedReconfigurationPlan) {

        EntropyService.applyReconfigurationPlan(plan)
    }

//    def computeAndApplyReconfigurationPlan(nodes: List[SGNodeRef]): Boolean = {
//
//        val initialConfiguration: Configuration = new SimpleConfiguration();
//
//
//        // building the entropy configuration
//
//        //    val physicalNodesWithVmsConsumption = nodes.map(n => {
//        //      ask(n, GetVmsWithConsumption()).asInstanceOf[PhysicalNode]
//        //    })
//
//        val physicalNodesWithVmsConsumption: List[PhysicalNode] = nodes.map(n => {
//            val entropyNode: Node = Main.getCurrentConfig.getAllNodes.get(n.getName)
//
//
//
//            val vms = Main.getCurrentConfig().getRunnings(entropyNode)
//            val vmsAsScalaArray:List[entropy.configuration.VirtualMachine]
//            = vms.toArray().map(_.asInstanceOf[entropy.configuration.VirtualMachine]).toList
//
//            PhysicalNode(new SGNodeRef(n.getName, n.getId), vmsAsScalaArray.map(vm =>
//                VirtualMachine(vm.getName, vm.getCPUDemand, ComputerSpecification(vm.getNbOfCPUs, vm.getMemoryDemand, vm.getCPUDemand))),
//                ComputerSpecification(entropyNode.getNbOfCPUs, entropyNode.getMemoryCapacity, entropyNode.getCPUCapacity)
//            )
//        })
//
//
//        physicalNodesWithVmsConsumption.foreach(physicalNodeWithVmsConsumption => {
//
//            val entropyNode = new SimpleNode(physicalNodeWithVmsConsumption.ref.toString,
//                physicalNodeWithVmsConsumption.specs.numberOfCPU,
//                physicalNodeWithVmsConsumption.specs.coreCapacity,
//                physicalNodeWithVmsConsumption.specs.ramCapacity);
//            initialConfiguration.addOnline(entropyNode);
//
//            physicalNodeWithVmsConsumption.machines.foreach(vm => {
//                val entropyVm = new SimpleVirtualMachine(vm.name,
//                    vm.specs.numberOfCPU,
//                    0,
//                    vm.specs.ramCapacity,
//                    vm.specs.coreCapacity,
//                    vm.specs.ramCapacity);
//                initialConfiguration.setRunOn(entropyVm, entropyNode);
//            })
//        })
//
//        EntropyService.computeAndApplyReconfigurationPlan(initialConfiguration)
//    }
}