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

import java.util.UUID
import scheduling.entropyBased.dvms2.SGNodeRef

object DvmsModel {


   object DvmsPartition {
      def apply(leader: SGNodeRef, initiator: SGNodeRef, nodes: List[SGNodeRef], state: DvmsPartititionState): DvmsPartition =
         DvmsPartition(leader, initiator, nodes, state, UUID.randomUUID())
   }

   case class DvmsPartition(leader: SGNodeRef, initiator: SGNodeRef, nodes: List[SGNodeRef], state: DvmsPartititionState, id: UUID)


   object DvmsPartititionState {
      case class Created() extends DvmsPartititionState {
         def isEqualTo(a: DvmsPartititionState): Boolean = a match {
            case Created() => true
            case _ => false
         }
      }

      case class Blocked() extends DvmsPartititionState {
         def isEqualTo(a: DvmsPartititionState): Boolean = a match {
            case Blocked() => true
            case _ => false
         }
      }

      case class Growing() extends DvmsPartititionState {
         def isEqualTo(a: DvmsPartititionState): Boolean = a match {
            case Growing() => true
            case _ => false
         }
      }

      case class Finishing() extends DvmsPartititionState {
         def isEqualTo(a: DvmsPartititionState): Boolean = a match {
            case Finishing() => true
            case _ => false
         }
      }

      case class Destroyed() extends DvmsPartititionState {
         def isEqualTo(a: DvmsPartititionState): Boolean = a match {
            case Destroyed() => true
            case _ => false
         }
      }
   }

   trait DvmsPartititionState {
      def isEqualTo(a: DvmsPartititionState): Boolean
   }


   case class ComputerSpecification(numberOfCPU: Int, ramCapacity: Int, coreCapacity: Int)

   case class PhysicalNode(ref: SGNodeRef, machines: List[VirtualMachine], url: String, specs: ComputerSpecification)

   case class VirtualMachine(name: String, cpuConsumption: Double, specs: ComputerSpecification)
}
