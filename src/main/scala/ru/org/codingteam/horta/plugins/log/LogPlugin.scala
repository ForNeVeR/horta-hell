package ru.org.codingteam.horta.plugins.log

import akka.actor.ActorRef
import akka.util.Timeout
import org.jivesoftware.smack.util.StringUtils
import org.joda.time.DateTime
import ru.org.codingteam.horta.localization.Localization
import ru.org.codingteam.horta.messages._
import ru.org.codingteam.horta.plugins._
import ru.org.codingteam.horta.protocol.Protocol
import ru.org.codingteam.horta.security.{CommonAccess, Credential}

import scala.concurrent.Future
import scala.concurrent.duration._

object SearchLogCommand

class LogPlugin extends BasePlugin
  with ParticipantProcessor
  with MessageProcessor
  with CommandProcessor
  with DataAccessingPlugin[LogRepository] {

  implicit val timeout = Timeout(60.seconds)

  import context.dispatcher

  val maxMessageLength = 50

  override protected def name: String = "log"

  override protected val schema: String = "log"
  override protected val createRepository = LogRepository.apply _

  override protected def commands: List[CommandDefinition] = List(
    CommandDefinition(CommonAccess, "search", SearchLogCommand))

  override protected def processParticipantJoin(time: DateTime,
                                                roomJID: String,
                                                participantJID: String,
                                                actor: ActorRef) {
    saveLogMessage(time, roomJID, participantJID, EnterType)
  }

  override protected def processParticipantLeave(time: DateTime,
                                                 roomJID: String,
                                                 participantJID: String,
                                                 reason: LeaveReason,
                                                 actor: ActorRef) {
    saveLogMessage(time, roomJID, participantJID, LeaveType, getReasonText(reason))
  }

  override protected def processMessage(time: DateTime, credential: Credential, message: String) {
    credential.roomId.map(roomId => saveLogMessage(time, roomId, credential.name, MessageType, message))
  }

  def processCommand(credential: Credential,
                     token: Any,
                     arguments: Array[String]) = {
    val location = credential.location
    (token, arguments) match {
      case (SearchLogCommand, Array(phrase)) =>
        for (response <- getSearchResponse(credential.roomId.get, phrase)) {
          Protocol.sendResponse(location, credential, response)
        }
      case _ => Protocol.sendResponse(location, credential, Localization.localize("Invalid command.")(credential))
    }
  }

  private def saveLogMessage(time: DateTime,
                             roomJID: String,
                             participantJID: String,
                             eventType: EventType) {
    val sender = StringUtils.parseResource(participantJID)
    saveLogMessage(time, roomJID, sender, EnterType, "")
  }

  private def saveLogMessage(time: DateTime,
                             roomJID: String,
                             sender: String,
                             eventType: EventType,
                             text: String) {
    val message = LogMessage(None, time, roomJID, sender, eventType, text)
    withDatabase(_.store(message))
  }

  private def getReasonText(reason: LeaveReason) = {
    reason match {
      case UserLeftReason(text) => s"User left: $text"
      case UserKickedReason(text) => s"User kicked: $text"
      case UserBannedReason(text) => s"User banned: $text"
      case UserRenamed(_) => reason.text
    }
  }

  private def getSearchResponse(room:String, phrase: String): Future[String] = {
    withDatabase(_.getMessages(room, phrase)) map { messages =>
      messages.map(message => s"${message.time} ${message.sender} ${prepareMessageText(message.text)}").mkString("\n")
    }
  }

  private def prepareMessageText(text: String) = {
    if (text.length > maxMessageLength) {
      text.substring(0, maxMessageLength - 1) + "…"
    } else {
      text
    }
  }

}
