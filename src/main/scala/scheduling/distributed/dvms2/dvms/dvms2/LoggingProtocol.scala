package scheduling.distributed.dvms2.dvms.dvms2

/**
 * Created by jonathan on 24/11/14.
 */
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
