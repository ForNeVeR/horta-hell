package ru.org.codingteam.horta.plugins

import akka.actor.{Actor, ActorLogging}
import ru.org.codingteam.horta.security.Credential

/**
 * CommandPlugin class used as base for all command plugins.
 */
abstract class CommandPlugin extends Actor with ActorLogging {
  def receive = {
    case GetCommands => sender ! commandDefinitions
    case ProcessCommand(credential, token, arguments) =>
      processCommand(credential, token, arguments)
  }

	/**
	 * A collection of (token -> scope) pairs, where token defines command token and scope - its scope.
	 * @return collection.
	 */
	def commandDefinitions: List[CommandDefinition]

	/**
	 * Process a command.
	 * @param credential a credential of a user executing the command.
	 * @param token token registered for command.
	 * @param arguments command argument array.
	 * @return string for replying the sender.
	 */
	def processCommand (
		credential: Credential,
		token: Any,
		arguments: Array[String]): Unit
}
