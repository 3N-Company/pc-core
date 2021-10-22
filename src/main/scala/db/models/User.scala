package db.models
import derevo.circe.{decoder, encoder}
import derevo.derive
import doobie.{Meta, Read}
import doobie.postgres.implicits._
import sttp.tapir.derevo.schema
import tofu.logging.derivation.{MaskMode, loggable, masked}

import java.util.UUID
/*
@derive(encoder, decoder, loggable, schema)
sealed trait Role


object Role {
  @derive(encoder, decoder, loggable, schema)
  case object Plain extends Role

  @derive(encoder, decoder, loggable, schema)
  case object Admin extends Role


  def toEnum(r: Role): String = r match {
    case Plain => "plain"
    case Admin => "admin"
  }

  def fromEnum(s: String): Option[Role] =
    Option(s).collect {
      case "plain" => Plain
      case "admin" => Admin
    }

  implicit val roleMeta: Meta[Role] =
    pgEnumStringOpt[Role]("role", Role.fromEnum, Role.toEnum)

}

*/

@derive(loggable, encoder, decoder, schema)
case class Credentials(username: String, @masked(MaskMode.Erase) password: String)


@derive(loggable, encoder, decoder, schema)
case class User (id: UUID, username: String,  role: String)



