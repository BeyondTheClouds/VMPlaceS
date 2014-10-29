package scheduling.entropyBased.dvms2.dvms

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


import java.io.{FileWriter, BufferedWriter, File, PrintWriter}
import scheduling.entropyBased.dvms2.dvms.LoggingProtocol._

trait LoggingMessage

case class LoggingPartition(initiator: String, leader: String, nodes: List[String])

object LoggingProtocol {

  case class PopState(time: Double, origin: String, state: String, value: String, data: String, duration: Double) extends LoggingMessage

  case class ExperimentInformation(time: Double, origin: String, serverCount: Int, serviceNodeCount:Int, vmCount: Int, algo: String) extends LoggingMessage

  case class ComputingSomeReconfigurationPlan(time: Double, origin: String, duration: Double, psize: Int, result: String) extends LoggingMessage

  case class ApplyingSomeReconfigurationPlan(time: Double, origin: String) extends LoggingMessage

  case class ApplicationSomeReconfigurationPlanIsDone(time: Double, origin: String) extends LoggingMessage

  case class HasCrashed(time: Double, origin: String) extends LoggingMessage

  case class IsBooked(time: Double, origin: String) extends LoggingMessage

  case class IsFree(time: Double, origin: String) extends LoggingMessage

  case class CurrentLoadIs(time: Double, origin: String, load: Double) extends LoggingMessage

  case class ViolationDetected(time: Double, origin: String) extends LoggingMessage

  case class UpdateMigrationCount(time: Double, origin: String, count: Int) extends LoggingMessage

  case class FirstOutIs(time: Double, origin: String, firstOut: Option[String]) extends LoggingMessage

  case class ForwardingPartition(time: Double, origin: String, partition: LoggingPartition, to: String) extends LoggingMessage

  case class AskingMigration(time: Double, origin: String, name: String, from: String, to: String) extends LoggingMessage

  case class StartingMigration(time: Double, origin: String, name: String, from: String, to: String) extends LoggingMessage

  case class FinishingMigration(time: Double, origin: String, name: String, from: String, to: String, duration: Double = -1) extends LoggingMessage

  case class AbortingMigration(time: Double, origin: String, name: String, from: String, to: String) extends LoggingMessage

}

object LoggingActor {

  val file = new File("events.json")
  val writer = new PrintWriter(new BufferedWriter(new FileWriter(file)))

  def write(message: LoggingMessage) = message match {


    case PopState(time: Double, origin: String, state: String, value: String, data: String, duration: Double) =>

      /* Quickly check wether value and data are json objects or not */
      val valueJsonEscaped = (value.headOption, value.lastOption) match {
        case (Some('{'), Some('}')) => value
        case _ => s""" "$value" """
      }

      val dataJsonEscaped = (data.headOption, data.lastOption) match {
        case (Some('{'), Some('}')) => data
        case _ => s""" "$data" """
      }

      writer.write( s"""{"event": "trace_event", "origin": "$origin", "state_name": "$state", "time": "$time", "value": $valueJsonEscaped, "data": $dataJsonEscaped, "duration": $duration}\n""")
//      writer.flush()

    case HasCrashed(time: Double, origin: String) =>
      writer.write( s"""{"event": "crash_event", "origin": "$origin"}\n""")
//      writer.flush()

    case ExperimentInformation(time: Double, origin: String, serverCount: Int, serviceNodeCount:Int, vmCount: Int, algo: String) =>
      writer.write( s"""{"event": "start_experiment", "origin": "$origin", "time": "$time", "server_count": $serverCount, "service_node_count": $serviceNodeCount, "vm_count": $vmCount, "algo": "$algo"}\n""")

    case ComputingSomeReconfigurationPlan(time: Double, origin: String, duration: Double, psize: Int, result: String) =>
      writer.write( s"""{"event": "computing_reconfiguration_plan", "origin": "$origin", "time": "$time", "psize": $psize, "duration": $duration, "result": "$result"}\n""")
//      writer.flush()

    case ApplyingSomeReconfigurationPlan(time: Double, origin: String) =>
      writer.write( s"""{"event": "applying_reconfiguration_plan", "origin": "$origin", "time": "$time"}\n""")
//      writer.flush()

    case ApplicationSomeReconfigurationPlanIsDone(time: Double, origin: String) =>
      writer.write( s"""{"event": "applying_reconfiguration_plan_is_done", "origin": "$origin", "time": "$time"}\n""")

    case IsBooked(time: Double, origin: String) =>
      writer.write( s"""{"event": "is_booked", "origin": "$origin", "time": "$time"}\n""")
//      writer.flush()

    case IsFree(time: Double, origin: String) =>
      writer.write( s"""{"event": "is_free", "origin": "$origin", "time": "$time"}\n""")
//      writer.flush()

    case FirstOutIs(time: Double, origin: String, firstOut: Option[String]) =>
      firstOut match {
        case Some(node) =>
          writer.write( s"""{"event": "first_out_is", "origin": "$origin", "time": "$time",  "first_out": "${node}", "first_out_defined": true }\n""")
        case None =>
          writer.write( s"""{"event": "first_out_is", "origin": "$origin", "time": "$time",  "first_out": "", "first_out_defined": false }\n""")
      }
//      writer.flush()

    case ForwardingPartition(time: Double, origin: String, partition: LoggingPartition, to: String) =>
      val partitionNodesAsToJsonArray: String = partition.nodes.map(s => s""" "$s" """).mkString("[", ",", "]")
      writer.write( s"""{"event": "forwarding_partition", "origin": "$origin", "time": "$time",  "initiator": "${partition.initiator}", "leader": "${partition.initiator}", "nodes": ${partitionNodesAsToJsonArray} , "to": "$to"}\n""")
      writer.flush()

    case AskingMigration(time: Double, origin: String, vm: String, from: String, to: String) =>
      writer.write( s"""{"event": "ask_migration", "origin": "$origin", "time": "$time",  "vm": "$vm", "from": "$from",  "to": "$to"}\n""")
//      writer.flush()

    case StartingMigration(time: Double, origin: String, vm: String, from: String, to: String) =>
      writer.write( s"""{"event": "start_migration", "origin": "$origin", "time": "$time",  "vm": "$vm", "from": "$from",  "to": "$to"}\n""")
//      writer.flush()

    case FinishingMigration(time: Double, origin: String, vm: String, from: String, to: String, duration: Double) =>
      writer.write( s"""{"event": "finish_migration", "origin": "$origin", "time": "$time",  "vm": "$vm", "from": "$from",  "to": "$to", "duration": $duration}\n""")
//      writer.flush()

    case AbortingMigration(time: Double, origin: String, vm: String, from: String, to: String) =>
      writer.write( s"""{"event": "abort_migration", "origin": "$origin", "time": "$time",  "vm": "$vm", "from": "$from",  "to": "$to"}\n""")
//      writer.flush()

    case CurrentLoadIs(time: Double, origin: String, load: Double) =>
      writer.write( s"""{"event": "cpu_load", "origin": "$origin", "time": "$time",  "value": "$load"}\n""")
//      writer.flush()

    case ViolationDetected(time: Double, origin: String) =>
      writer.write( s"""{"event": "overload", "origin": "$origin", "time": "$time"}\n""")
//      writer.flush()

    case UpdateMigrationCount(time: Double, origin: String, count: Int) =>
      writer.write( s"""{"event": "migration_count", "origin": "$origin", "time": "$time", "value": "$count"}\n""")
//      writer.flush()

    case _ =>
  }

}
