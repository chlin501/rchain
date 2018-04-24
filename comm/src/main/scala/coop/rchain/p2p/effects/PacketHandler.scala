package coop.rchain.p2p.effects

import coop.rchain.comm.protocol.rchain.Packet
import cats._, cats.data._, cats.implicits._
import coop.rchain.catscontrib._, Catscontrib._

trait PacketHandler[F[_]] {
  def handlePacket(packet: Packet): F[String]
}

object PacketHandler extends PacketHandlerInstances {
  def apply[F[_]: PacketHandler]: PacketHandler[F] = implicitly[PacketHandler[F]]

  def forTrans[F[_]: Monad, T[_[_], _]: MonadTrans](
      implicit C: PacketHandler[F]): PacketHandler[T[F, ?]] =
    (packet: Packet) => C.handlePacket(packet).liftM[T]

  class NOPPacketHandler[F[_]: Applicative] extends PacketHandler[F] {
    def handlePacket(packet: Packet): F[String] = "".pure[F]
  }

}

sealed abstract class PacketHandlerInstances {
  implicit def eitherTPacketHandler[E, F[_]: Monad: PacketHandler[?[_]]]
    : PacketHandler[EitherT[F, E, ?]] =
    PacketHandler.forTrans[F, EitherT[?[_], E, ?]]
}
