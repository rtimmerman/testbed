trait Manager {
  def afterRun(): Unit
  def dispatch(): Unit
}
