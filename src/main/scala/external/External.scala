package external

import distage.{ModuleDef, TagK}

object External {

  def Module[F[_]: TagK]: ModuleDef = new ModuleDef {
    make[Normalization[F]].from[Normalization[F]]
  }

}
