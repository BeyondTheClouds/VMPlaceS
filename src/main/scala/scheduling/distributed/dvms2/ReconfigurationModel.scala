package scheduling.distributed.dvms2

/**
  * Created by jonathan on 2/13/18.
  */
object ReconfigurationModel {

  trait ReconfigurationAction

  case class MakeMigration(val from: String, val to: String, val vmName: String) extends ReconfigurationAction

  trait ReconfigurationResult

  case class ReconfigurationlNoSolution() extends ReconfigurationResult
  case class ReconfigurationSolution(val actions: java.util.Map[String, java.util.List[ReconfigurationAction]]) extends ReconfigurationResult
}
