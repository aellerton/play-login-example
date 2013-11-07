import play.libs.{Json => PlayJson}
import play.api._
import state.JsonMapper

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    // Without the DefaultScalaModule, all attempts to serialize scala case classes
    // and the like with Play's built-in Json object will fail silently.
    PlayJson.setObjectMapper(JsonMapper.mapper)
  }

}
