package ru.org.codingteam.horta.plugins.pet

import akka.actor.{Props, Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import org.jivesoftware.smack.util.StringUtils
import ru.org.codingteam.horta.database.{ReadObject, StoreObject}
import ru.org.codingteam.horta.plugins.pet.Pet.PetTick
import ru.org.codingteam.horta.plugins.pet.commands.AbstractCommand
import ru.org.codingteam.horta.protocol.Protocol
import ru.org.codingteam.horta.security.Credential
import ru.org.codingteam.horta.messages.GetParticipants

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class Pet(roomId: String, location: ActorRef) extends Actor {

  import context.dispatcher

  implicit val timeout = Timeout(5 minutes)

  private val store = context.actorSelection("/user/core/store")
  private var petData: Option[PetData] = None
  private var coins: ActorRef = null
  
  override def preStart() {
    context.system.scheduler.schedule(15 seconds, 360 seconds, self, Pet.PetTick)
    coins = context.actorOf(Props(new PetCoinStorage(roomId)))
  } 

  override def receive = {
    case PetTick => processTick()
    case Pet.ExecuteCommand(command, invoker, arguments) => processCommand(command, invoker, arguments)
  }

  private def processTick() = processAction { pet =>
    val nickname = pet.nickname
    var alive = pet.alive
    var health = pet.health
    var satiation = pet.satiation
    val coinHolders = Await.result((coins ? GetPTC()).mapTo[Map[String, Int]], 1.minute).keys

    val aggressiveAttack = List(
      " яростно набрасывается на ",
      " накидывается на ",
      " прыгает, выпустив когти, на ",
      " с рыком впивается в бедро "
    )

    val losePTC = List(
      " от голода, крепко вцепившись зубами и выдирая кусок ткани штанов с кошельком",
      " раздирая в клочья одежду от голода и давая едва увернуться ценой потери выпавшего кошелька",
      " от жуткого голода, сжирая одежду и кошелёк"
    )

    val searchingForFood = List(
      " пытается сожрать все, что найдет",
      " рыщет в поисках пищи",
      " жалобно скулит и просит еды",
      " рычит от голода",
      " тихонько поскуливает от боли в пустом желудке",
      " скребёт пол в попытке найти пропитание",
      " переворачивает всё вверх дном в поисках еды",
      " ловит зубами блох, пытаясь ими наесться",
      " грызёт ножку стола, изображая вселенский голод",
      " демонстративно гремит миской, требовательно ворча",
      " плотоядно смотрит на окружающих, обнажив зубы",
      " старательно принюхивается, пытаясь уловить хоть какой-нибудь запах съестного",
      " плачет от голода, утирая слёзы хвостом"
    )

    val becomeDead = List(
      " умер в забвении с гримасой страдания на морде",
      " корчится в муках и умирает",
      " агонизирует, сжимая зубы в предсмертных судорогах",
      " издал тихий рык и испустил дух"
    )

    val lowHealth = List(
      " забился в самый темный угол конфы и смотрит больными глазами в одну точку",
      " лежит и еле дышит, хвостиком едва колышет",
      " жалобно поскуливает, волоча заднюю лапу",
      " завалился на бок и окинул замутнённым болью взором конфу",
      " едва дышит, издавая хриплые звуки и отхаркивая кровавую пену"
    )

    if (pet.alive) {
      health -= 1
      satiation -= 2

      if (satiation <= 0 || health <= 0) {
        alive = false
        coins ! UpdateAllPTC("pet death", -1)
        sayToEveryone(location, s"$nickname" + pet.randomChoice(becomeDead) + ". Все теряют по 1PTC.")
      } else if (satiation <= 12 && satiation > 5 && satiation % 3 == 0) { // 12, 9, 6
        if (pet.randomGen.nextInt(10) == 0 && coinHolders.size > 0) {
          val map = Await.result((location ? GetParticipants()).mapTo[Map[String, Any]], 5.seconds)
          val possibleVictims = map.keys map ((x: String) => StringUtils.parseResource(x))
          val victim = pet.randomChoice((coinHolders.toSet & possibleVictims.toSet).toList)
          coins ! UpdateUserPTCWithOverflow("pet aggressive attack", victim, -3)
          sayToEveryone(location, s"$nickname" + pet.randomChoice(aggressiveAttack) + victim + pet.randomChoice(losePTC) + s". $victim теряет 3PTC.")
          satiation = 100
        } else {
          if (pet.randomGen.nextInt(3) != 0) {
            sayToEveryone(location, s"$nickname" + pet.randomChoice(searchingForFood) + ".")
          }
        }
      } else if (health <= 10 && pet.health > 9) {
        sayToEveryone(location, s"$nickname" + pet.randomChoice(lowHealth) + ".")
      }

      pet.copy(alive = alive, health = health, satiation = satiation)
    } else {
      pet
    }
  }

  private def processCommand(command: AbstractCommand, invoker: Credential, arguments: Array[String]) =
    processAction { pet =>
      val (newPet, response) = command(pet, coins, invoker, arguments)
      Protocol.sendResponse(location, invoker, response)
      newPet
    }

  private def processAction(action: PetData => PetData) {
    val pet = getPetData()
    val newPet = action(pet)
    setPetData(newPet)
  }
  
  private def getPetData(): PetData = {
    petData match {
      case Some(data) => data
      case None =>
        val data = readStoredData() match {
          case Some(dbData) => dbData
          case None => PetData.default
        }

        petData = Some(data)
        data
    }
  }

  private def setPetData(pet: PetData) {
    val Some(_) = Await.result(store ? StoreObject("pet", Some(roomId), pet), 5 minutes)
    petData = Some(pet)
  }

  private def readStoredData(): Option[PetData] = {
    val request = store ? ReadObject("pet", roomId)
    Await.result(request, 5 minutes).asInstanceOf[Option[PetData]]
  }

  def sayToEveryone(location: ActorRef, text: String) {
    val credential = Credential.empty(location)
    Protocol.sendResponse(location, credential, text)
  }
}

object Pet {
  case object PetTick
  
  case class ExecuteCommand(command: AbstractCommand, invoker: Credential, arguments: Array[String])
}
