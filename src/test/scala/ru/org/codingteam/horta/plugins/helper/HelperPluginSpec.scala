package ru.org.codingteam.horta.plugins.helper

import akka.actor.Props
import ru.org.codingteam.horta.localization.{LocaleDefinition, Localization}
import ru.org.codingteam.horta.plugins.helper.{HelperPlugin, ManCommand}
import ru.org.codingteam.horta.plugins.ProcessCommand
import ru.org.codingteam.horta.protocol.SendResponse
import ru.org.codingteam.horta.security.{CommonAccess, Credential}
import ru.org.codingteam.horta.test.TestKitSpec

class HelperPluginSpec extends TestKitSpec {

  val credential = Credential(
    testActor,
    LocaleDefinition("en"),
    CommonAccess,
    Some("testroom"),
    "testuser",
    Some("testuser")
  )

  val errorMessage = Localization.localize("I'm sorry, %s. I'm afraid I can't help you with that.", credential.locale).format(credential.name)

  "HelperPlugin" should {
    val plugin = system.actorOf(Props[HelperPlugin])
    "answer with some text when no arguments are given" in {
      plugin ! ProcessCommand(credential, ManCommand, Array())
      val message = expectMsgType[SendResponse](timeout.duration)
      assert(!message.text.isEmpty)
      assert(!message.text.equals(errorMessage))
    }
    "answer with command usage text when given a command name as an argument" in {
      plugin ! ProcessCommand(credential, ManCommand, Array("say"))
      val message = expectMsgType[SendResponse](timeout.duration)
      assert(!message.text.isEmpty)
      assert(!message.text.equals(errorMessage))
    }
    "answer with an error message when given more than one arguments" in {
      plugin ! ProcessCommand(credential, ManCommand, Array("invalid", "arguments"))
      val message = expectMsgType[SendResponse](timeout.duration)
      assert(!message.text.isEmpty)
      assert(message.text.equals(errorMessage))
      assert(message.text.contains(credential.name))
    }
    "answer with an error message when given an unknown command name" in {
      plugin ! ProcessCommand(credential, ManCommand, Array("notacommand"))
      val message = expectMsgType[SendResponse](timeout.duration)
      assert(!message.text.isEmpty)
      assert(message.text.equals(errorMessage))
      assert(message.text.contains(credential.name))
    }
  }

}
