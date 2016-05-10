import java.io.IOException

import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math._
import scala.io.StdIn
import spray.json.DefaultJsonProtocol

case class MovieInfo(imdbID: String, Title: Option[String], Year: Option[String], Director: Option[String], Actors: Option[String], Plot: Option[String])

case class MovieRequest(imdbID: String)

trait Protocols extends DefaultJsonProtocol {
  implicit val movieInfoFormat = jsonFormat6(MovieInfo.apply)
  implicit val movieRequestFormat = jsonFormat1(MovieRequest.apply)
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  lazy val movieApiConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.movie-api.host"), config.getInt("services.movie-api.port"))


  def movieApiRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(movieApiConnectionFlow).runWith(Sink.head)

  def fetchMovieInfo(id: MovieRequest): Future[Either[String, MovieInfo]] = {
    movieApiRequest(RequestBuilding.Get(Uri("/").withQuery(Query("i" -> id.imdbID, "r" -> "json")))).flatMap { response =>
      response.status match {
        case OK => Unmarshal(response.entity).to[MovieInfo].map(Right(_))
        case BadRequest => Future.successful(Left(id + ": incorrect movie id format"))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"omdbIP request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }

  val routes = {
    logRequestResult("akka-http-microservice") {
      pathPrefix("movie") {
        (get & entity(as[MovieRequest])) { movie =>
          complete {
            fetchMovieInfo(movie).map[ToResponseMarshallable] {
              case Right(movieInfo) => movieInfo
              case Left(errorMessage) => BadRequest -> errorMessage
            }
          }
        } ~
        (get & path(Segment)) { id =>
          complete {
            fetchMovieInfo(MovieRequest(id)).map[ToResponseMarshallable] {
              case Right(movieInfo) => movieInfo
              case Left(errorMessage) => BadRequest -> errorMessage
            }
          }
        }
      }
    }
  }
}

object AkkaHttpMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  val bindingFuture = Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))

  println("server online at " + config.getString("http.interface") + ":" + config.getString("http.port"))
  StdIn.readLine()
  bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
}
