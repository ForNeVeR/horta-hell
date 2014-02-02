package ru.org.codingteam.horta.protocol.jabber

import org.jivesoftware.smackx.muc.{MultiUserChat, DefaultParticipantStatusListener}
import akka.actor.ActorRef
import ru.org.codingteam.horta.messages._

class MucParticipantStatusListener(muc: MultiUserChat, room: ActorRef) extends DefaultParticipantStatusListener {
  override def joined(participant: String) {
    val occupant = muc.getOccupant(participant)
    val affilation = occupant.getAffiliation

    room ! UserJoined(participant, affilation)
  }

  override def left(participant: String) {
    room ! UserLeft(participant)
  }

  override def ownershipGranted(participant: String) {
    room ! OwnershipGranted(participant)
  }

  override def ownershipRevoked(participant: String) {
    room ! OwnershipRevoked(participant)
  }

  override def adminGranted(participant: String) {
    room ! AdminGranted(participant)
  }

  override def adminRevoked(participant: String) {
    room ! AdminRevoked(participant)
  }

  override def nicknameChanged(participant: String, newNickname: String) {
    room ! NicknameChanged(participant, newNickname)
  }
}
