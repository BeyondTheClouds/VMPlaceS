package scheduling.distributed.dvms2.dvms.dvms3

object MayFail {
  def unit[T](t: => T): MayFail[T] = MayFailImpl(t)
}

trait MayFail[T] {

  def bind[R](f: (T => R)): Option[R]

  def watch(failureCallBack: T => Unit)
}

case class Failed[T]() extends MayFail[T] {
  def bind[R](f: (T => R)): Option[R] = {
    None
  }

  def watch(failureCallBack: T => Unit) {
    // nothing to do here!
  }
}

case class MayFailList[T](l: List[MayFail[T]]) {
  def executeInProtectedSpace[R](f: (T => R)): Option[List[R]] = {
    val listOfFutures = l map {
      m => m.bind(f)
    }
    Some(listOfFutures.flatten)
  }

  def watch(failureCallBack: T => Unit) {
    // nothing to do here!
  }
}

case class CallBack[T](f: (T => Unit), id: Int)

case class MayFailImpl[T](var unsafeResource: T) extends MayFail[T] {

  var callback: CallBack[T] = CallBack(_ => None, 0)

  def watch(fcb: T => Unit) {
    callback = CallBack(fcb, callback.id + 1)
  }

  def bind[R](f: (T => R)): Option[R] = {
    try {
      Some(f(unsafeResource))
    } catch {
      case e: Throwable =>
        callback.f(unsafeResource)
        None
    }
  }

  // only for testing purpose
  def destroyResource() {
    unsafeResource = null.asInstanceOf[T]
  }

  override def toString: String = s"MayFail($unsafeResource)"
}