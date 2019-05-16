package uk.ac.wellcome.messaging.sns

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.messaging.fixtures.SNS
import uk.ac.wellcome.messaging.fixtures.SNS.Topic

class SNSWriterTest
    extends FunSpec
    with ScalaFutures
    with Matchers
    with SNS
    with IntegrationPatience
    with JsonAssertions {

  it("sends a message to SNS") {
    withLocalSnsTopic { topic =>
      withSNSWriter(topic) { snsWriter =>
        val message = "sns-writer-test-message"
        val subject = "sns-writer-test-subject"
        val future = snsWriter.writeMessage(
          message = message,
          subject = subject
        )

        whenReady(future) { _ =>
          val messages = listMessagesReceivedFromSNS(topic)
          messages should have size 1
          messages.head.message shouldBe message
          messages.head.subject shouldBe subject
        }
      }
    }
  }

  case class Shape(sides: Int, colour: String)

  it("encodes a case class as JSON before sending it to SNS") {
    withLocalSnsTopic { topic =>
      withSNSWriter(topic) { snsWriter =>
        val message = Shape(sides = 5, colour = "red")

        val future = snsWriter.writeMessage(
          message = message,
          subject = "shape sorter subject"
        )

        whenReady(future) { _ =>
          val messages = listMessagesReceivedFromSNS(topic)
          messages should have size 1

          assertJsonStringsAreEqual(
            messages.head.message,
            toJson(message).get
          )
        }
      }
    }
  }

  sealed trait Container {}

  case class Box(sides: Int) extends Container
  case class Bottle(height: Int) extends Container

  it("encodes a case class using the type parameter before sending it to SNS") {
    withLocalSnsTopic { topic =>
      withSNSWriter(topic) { snsWriter =>
        val message = Box(sides = 4)

        val future = snsWriter.writeMessage[Container](
          message = message,
          subject = "box subject"
        )

        whenReady(future) { _ =>
          val messages = listMessagesReceivedFromSNS(topic)
          messages should have size 1

          fromJson[Container](messages.head.message).get shouldBe message
        }
      }
    }
  }

  it("returns a failed Future if it fails to publish the message") {
    withSNSWriter(Topic("does-not-exist")) { snsWriter =>
      val future =
        snsWriter.writeMessage(message = "someMessage", subject = "subject")

      whenReady(future.failed) { exception =>
        exception.getMessage should not be empty
      }
    }
  }
}
