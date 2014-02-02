package ru.org.codingteam.horta.protocol.jabber

import akka.actor.{ActorRef, Actor, ActorLogging}
import ru.org.codingteam.horta.security._
import java.util.regex.Pattern
import ru.org.codingteam.horta.messages.OwnershipRevoked
import ru.org.codingteam.horta.messages.SendMucMessage
import ru.org.codingteam.horta.messages.UserMessage
import ru.org.codingteam.horta.messages.AdminRevoked
import scala.Some
import ru.org.codingteam.horta.messages.UserLeft
import ru.org.codingteam.horta.messages.UserJoined
import ru.org.codingteam.horta.messages.NicknameChanged
import ru.org.codingteam.horta.messages.CoreMessage
import ru.org.codingteam.horta.messages.OwnershipGranted
import ru.org.codingteam.horta.messages.AdminGranted
import ru.org.codingteam.horta.messages.SendResponse

/**
 * Multi user chat message handler.
 */
class MucMessageHandler(val protocol: ActorRef, val roomJid: String) extends Actor with ActorLogging {

  val core = context.actorSelection("/user/core")

  var participants = Map[String, Affinity]()

  def receive = {
    case UserJoined(participant, affilationName) =>
      val affilation = affilationName match {
        case "owner" => Owner
        case "admin" => Admin
        case _ => User
      }

      log.info(s"$participant joined as $affilation")
      participants += participant -> affilation

    case UserLeft(participant) =>
      participants -= participant
      log.info(s"$participant left")

    case OwnershipGranted(participant) =>
      participants = participants.updated(participant, Owner)
      log.info(s"$participant became an owner")

    case OwnershipRevoked(participant) =>
      participants = participants.updated(participant, User)
      log.info(s"$participant ceased to be an owner")

    case AdminGranted(participant) =>
      participants = participants.updated(participant, Admin)
      log.info(s"$participant became an admin")

    case AdminRevoked(participant) =>
      participants = participants.updated(participant, User)
      log.info(s"$participant ceased to be an admin")

    case NicknameChanged(participant, newNick) =>
      val newParticipant = jidByNick(newNick)
      val access = participants(participant)
      participants = participants - participant + (newParticipant -> access)
      log.info(s"$participant changed nick to $newNick")

    case UserMessage(message) => {
      val jid = message.getFrom
      val text = message.getBody

      val credential = getCredential(jid)
      core ! CoreMessage(credential, text)
    }

    case SendResponse(credential, text) => {
      val response = prepareResponse(credential.name, text)
      sendMessage(response)
    }
  }

  def getCredential(jid: String) = {
    // TODO: Use admin access to know the real JID if possible.
    // TODO: If user known to be an owner - give him the GlobalAccess level.
    val accessLevel = participants.get(jid) match {
      case None =>
        log.warning(s"Cannot find participant $jid in the participant list")
        CommonAccess
      case Some(Owner) | Some(Admin) => RoomAdminAccess
      case Some(User) => CommonAccess
    }

    Credential(self, accessLevel, Some(roomJid), nickByJid(jid), Some(jid))
  }

  def jidByNick(nick: String) = s"$roomJid/$nick"

  def nickByJid(jid: String) = {
    val args = jid.split('/')
    if (args.length > 1) {
      args(1)
    } else {
      args(0)
    }
  }

  def sendMessage(message: String) {
    protocol ! SendMucMessage(roomJid, message)
  }

  def prepareResponse(recipient: String, text: String) = {
    var message = text
    for (nick <- participants.keys.map(nickByJid)) {
      if (nick != recipient && nick.length > 0) {
        val quoted = Pattern.quote(nick)
        val pattern = s"(?<=\\W|^)$quoted(?=\\W|$$)"
        val replacement = nick.substring(0, 1) + "…"
        message = message.replaceAll(pattern, replacement)
      }
    }

    if (recipient.isEmpty) {
      message
    } else {
      s"$recipient: $message"
    }
  }
}
