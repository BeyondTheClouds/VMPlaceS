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

import org.discovery.dvms.dvms.DvmsModel._
import scheduling.entropyBased.dvms2.SGNodeRef

trait DvmsMessage

object DvmsProtocol {

   case class ThisIsYourNeighbor(neighbor: SGNodeRef)
   case class YouMayNeedToUpdateYourFirstOut(oldNeighbor: Option[SGNodeRef], newNeighbor: SGNodeRef)
   case class CpuViolationDetected()

   // Message used for the base of DVMS
   case class DissolvePartition(reason: String)
   case class TransmissionOfAnISP(currentPartition: DvmsPartition)
   case class IAmTheNewLeader(partition: DvmsPartition)

   // Message used for the merge of partitions
   case class IsThisVersionOfThePartitionStillValid(partition: DvmsPartition)
   case class CanIMergePartitionWithYou(partition: DvmsPartition, contact: SGNodeRef)
   case class ChangeTheStateOfThePartition(newState: DvmsPartititionState)

   // Message for the resiliency
//   case class AskTimeoutDetected(e: AskTimeoutException)
   case class FailureDetected(node: SGNodeRef)
   case class CheckTimeout()


}
