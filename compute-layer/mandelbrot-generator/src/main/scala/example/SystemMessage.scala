package example

enum SystemMessage(message: String) {
  case STOP extends SystemMessage("stop")

  def getMessage(): String = message
}
