package scheduling.distributed.dvms2.dvms.dvms2

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

import scheduling.distributed.dvms2.SGNodeRef
import java.util.UUID
import scheduling.distributed.dvms2.dvms.dvms2.DvmsModel._


object DvmsProtocol {

  case class ThisIsYourNeighbor(neighbor: SGNodeRef)

  case class CpuViolationDetected()

  // Message used for the base of DVMS
  case class SetCurrentPartition(partition: DvmsPartition)
  case class DissolvePartition(partitionId: String, reason: String)
  case class TransmissionOfAnISP(currentPartition: DvmsPartition)

  // Message used for the merge of partitions
  case class IsThisVersionOfThePartitionStillValid(partition: DvmsPartition)
  case class CanIMergePartitionWithYou(partition: DvmsPartition, contact: SGNodeRef)
  case class ChangeTheStateOfThePartition(newState: DvmsPartititionState)

  // Message for the resiliency
  case class FailureDetected(node: SGNodeRef)
  case class CheckTimeout()
  case class SnoozeTimeout()
}
