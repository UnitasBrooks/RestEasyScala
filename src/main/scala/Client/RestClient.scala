package Client

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import play.api.libs.ws.DefaultBodyReadables._
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
 * REST easy friend, this is the simplest REST client for scala imaginable.
 *
 * Usage:
 *  val client = RestClient.defaultClient
 *  client.get(...)
 *  client.end()
 *
 *  ~OR~
 *  RestClient.withDefaultClient(client => client.get(...))
 *
 *  ~OR~
 *  RestClient.get(...)
 */

sealed trait RestClient {
  def get(url: String, headers: Map[String, String], timeoutSeconds: Int): Try[String]
  def post(url: String, body: String, headers: Map[String, String], timeoutSeconds: Int): Try[String]
  def put(url: String, body: String, headers: Map[String, String], timeoutSeconds: Int): Try[String]
  def delete(url: String, headers: Map[String, String], timeoutSeconds: Int): Try[String]
  def end(): Unit
}

object RestClient {
  def defaultClient: RestClient = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    val wsClient = StandaloneAhcWSClient()(materializer)
    new RestClientImpl(wsClient)
  }

  def withDefaultClient[T](f: RestClient => T): T = {
    val client = defaultClient
    try {
      f(client)
    } finally {
      client.end()
    }
  }

  def get(url: String, headers: Map[String, String] = Map(), timeoutSeconds: Int = 30): Try[String] = {
    withDefaultClient { client =>
      client.get(url, headers, timeoutSeconds)
    }
  }

  def delete(url: String, headers: Map[String, String] = Map(), timeoutSeconds: Int = 30): Try[String] = {
    withDefaultClient { client =>
      client.delete(url, headers, timeoutSeconds)
    }
  }

  def put(url: String, body: String, headers: Map[String, String] = Map(), timeoutSeconds: Int = 30): Try[String] = {
    withDefaultClient { client =>
      client.put(url, body, headers, timeoutSeconds)
    }
  }

  def post(url: String, body: String, headers: Map[String, String] = Map(), timeoutSeconds: Int = 30): Try[String] = {
    withDefaultClient { client =>
      client.post(url, body, headers, timeoutSeconds)
    }
  }
}

private[this] class RestClientImpl(wsClient: StandaloneAhcWSClient)(implicit system: ActorSystem) extends RestClient {
  def get(url: String, headers: Map[String, String] = Map(), timeoutSeconds: Int = 30): Try[String] = {
    await(wsGet(url, headers), timeoutSeconds)
  }

  def post(url: String, body: String, headers: Map[String, String] = Map(), timeoutSeconds: Int = 30): Try[String] = {
    await(wsPost(url, body, headers), timeoutSeconds)
  }

  override def put(url: String, body: String, headers: Map[String, String], timeoutSeconds: Int): Try[String] = {
    await(wsPut(url, body, headers), timeoutSeconds)
  }

  override def delete(url: String, headers: Map[String, String], timeoutSeconds: Int): Try[String] = {
    await(wsDelete(url, headers), timeoutSeconds)
  }

  private[this] def wsGet(url: String, headers: Map[String, String]): Future[Try[String]] = {
    parseResponse {
      wsClient.url(url).withHttpHeaders(headers.toList: _*).get
    }
  }

  private[this] def wsPost(url: String, body: String, headers: Map[String, String]): Future[Try[String]] = {
    parseResponse { () =>
      wsClient.url(url).withHttpHeaders(headers.toList: _*).post[String](body)
    }
  }

  private[this] def wsPut(url: String, body: String, headers: Map[String, String]): Future[Try[String]] = {
    parseResponse { () =>
      wsClient.url(url).withHttpHeaders(headers.toList: _*).put[String](body)
    }
  }

  private[this] def wsDelete(url: String, headers: Map[String, String]): Future[Try[String]] = {
    parseResponse {
      wsClient.url(url).withHttpHeaders(headers.toList: _*).delete
    }
  }

  private[this] def await(attempt: Future[Try[String]], timeout: Int): Try[String] = {
    for {
      triedAwaitResult <- Try(Await.result(attempt, timeout.seconds))
      result <- triedAwaitResult
    } yield result
  }

  private[this] def parseResponse(f: () => Future[StandaloneWSRequest#Response]): Future[Try[String]] = {
    f().map { response =>
      val body = response.body[String]
      Success(body)
    }.recover {
      case e: Exception => Failure(e).asInstanceOf[Try[String]]
    }
  }

  def end(): Unit = {
    system.terminate()
    wsClient.close()
  }
}
