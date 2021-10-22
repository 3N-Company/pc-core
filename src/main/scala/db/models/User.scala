package db.models
import derevo.circe.{decoder, encoder}
import derevo.derive
import doobie.{Meta, Read}
import doobie.postgres.implicits._
import io.circe.{Decoder, Encoder}
import sttp.tapir.derevo.schema
import tofu.logging.derivation.{MaskMode, loggable, masked}
import cats.syntax.either._
import sttp.tapir.{Schema, Validator}

import java.util.UUID

@derive(loggable)
sealed trait Role

object Role {
  @derive(loggable)
  case object Plain extends Role

  @derive(loggable)
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

  implicit val roleEncode: Encoder[Role] =
    Encoder[String].contramap {
      case Plain => toEnum(Plain)
      case Admin => toEnum(Admin)
    }

  implicit val roleDecoder: Decoder[Role] =
    Decoder[String].emap(s => fromEnum(s).toRight(s"Role $s does not exist"))

  implicit val roleSchema: Schema[Role] =
    Schema.string[Role].validate {
      Validator.enumeration(List(Plain, Admin), (r: Role) => Some(toEnum(r)))
    }

}

@derive(loggable, encoder, decoder, schema)
case class Credentials(
    username: String,
    @masked(MaskMode.Erase) password: String
)

@derive(loggable, encoder, decoder, schema)
case class User(id: UUID, username: String, role: Role)
