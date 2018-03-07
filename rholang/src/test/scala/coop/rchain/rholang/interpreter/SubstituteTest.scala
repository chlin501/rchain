package coop.rchain.rholang.interpreter

import org.scalatest.{FlatSpec, Matchers}
import Substitute._
import scala.collection.immutable.BitSet
import scala.collection.immutable.HashMap

class VarSubSpec extends FlatSpec with Matchers {

  "FreeVar" should "throw an error" in {
    val env = HashMap(0 -> Par(GPrivate()))
    an[Error] should be thrownBy subOrDec(FreeVar(0))(env)
  }

  "BoundVar" should "be substituted for process" in {
    val source = Par(GPrivate())
    val env    = HashMap(0 -> source)
    val result = subOrDec(BoundVar(0))(env)
    result should be(Right(source))
  }

  "BoundVar" should "be substituted with renamed expression" in {
    val source  = Par(Send(ChanVar(BoundVar(0)), List(Par()), false, 0, BitSet(0)))
    val _source = Par(Send(ChanVar(BoundVar(1)), List(Par()), false, 0, BitSet(1)))
    val env     = HashMap(0 -> source)
    val result  = subOrDec(BoundVar(0))(env)
    result should be(Right(_source))
  }

  "BoundVar" should "throw an error" in {
    val env = HashMap(1 -> Par(GPrivate()))
    an[Error] should be thrownBy subOrDec(BoundVar(0))(env)
  }

  "BoundVar" should "be decremented by environment level" in {
    val env    = HashMap(0 -> Par(GPrivate()), 1 -> Par(GPrivate()))
    val result = subOrDec(BoundVar(2))(env)
    result should be(Left(BoundVar(0)))
  }
}

class ChannelSubSpec extends FlatSpec with Matchers {

  "ChanVar" should "be decremented by environment level" in {
    val env    = HashMap(0 -> Par(GPrivate()), 1 -> Par(GPrivate()))
    val result = substitute(ChanVar(BoundVar(2)))(env)
    result should be(ChanVar(BoundVar(0)))
  }

  "Quote" should "substitute quoted process" in {
    val env    = HashMap(0 -> Par(GPrivate()))
    val par    = Par(Send(ChanVar(BoundVar(1)), List(Par()), false, 0, BitSet(1)))
    val target = Quote(par)
    val result = substitute(target)(env)
    result should be(Quote(Par(Send(ChanVar(BoundVar(0)), List(Par()), false, 0, BitSet(0)))))
  }

  "Channel" should "be substituted for a Quote" in {
    val source = Par(GPrivate())
    val env    = HashMap(0 -> source)
    val result = substitute(ChanVar(BoundVar(0)))(env)
    result should be(Quote(source))
  }
}

class SendSubSpec extends FlatSpec with Matchers {

  "Send" should "decrement subject Channel and substitute object processes" in {
    val env    = HashMap(0 -> Par(GPrivate()), 1 -> Par(GPrivate()))
    val result = substitute(Send(ChanVar(BoundVar(2)), List(Par()), false, 0, BitSet(2)))(env)
    result should be(Send(ChanVar(BoundVar(0)), List(Par()), false, 0, BitSet(0)))
  }

  "Send" should "substitute Channel for Quote" in {
    val source0 = Par(GPrivate())
    val source1 = Par(GPrivate())
    val env     = HashMap(0 -> source0, 1 -> source1)
    val result = substitute(
      Send(Quote(Send(ChanVar(BoundVar(0)), List(Par()), false, 0, BitSet(0))),
           List(Par()),
           false,
           0,
           BitSet(0)))(env)
    result should be(
      Send(
        Quote(Send(Quote(source0), List(Par()), false, 0, BitSet())),
        List(Par()),
        false,
        0,
        BitSet()
      )
    )
  }

  "Send" should "substitute all Channels for Quotes" in {
    val source = Par(GPrivate())
    val target = Send(ChanVar(BoundVar(0)),
                      List(Par(Send(ChanVar(BoundVar(0)), List(Par()), false, 0, BitSet(0)))),
                      false,
                      0,
                      BitSet(0))
    val _target =
      Send(Quote(source),
           List(Par(Send(Quote(source), List(Par()), false, 0, BitSet()))),
           false,
           0,
           BitSet())
    val env    = HashMap(0 -> source)
    val result = substitute(target)(env)
    result should be(_target)
  }

  "Send" should "substitute all channels for renamed, quoted process in environment" in {
    val chan0  = ChanVar(BoundVar(0))
    val chan1  = ChanVar(BoundVar(1))
    val source = Par(New(1, Send(chan0, List(Par()), false, 0, BitSet(0)), BitSet()))
    val target =
      Send(chan0, List(Par(Send(chan0, List(Par()), false, 0, BitSet(0)))), false, 0, BitSet(0))
    val env    = HashMap(0 -> source)
    val result = substitute(target)(env)
    result should be(
      Send(
        Quote(New(1, Send(chan1, List(Par()), false, 0, BitSet(1)), BitSet())),
        List(
          Par(
            Send(
              Quote(Par(New(1, Send(chan1, List(Par()), false, 0, BitSet(1)), BitSet()))),
              List(Par()),
              false,
              0,
              BitSet()
            )
          )
        ),
        false,
        0,
        BitSet()
      )
    )
  }
}

class NewSubSpec extends FlatSpec with Matchers {

  "New" should "only substitute body of expression" in {
    val source = Par(GPrivate())
    val env    = HashMap(0 -> source)
    val target =
      New(1, Par(Send(ChanVar(BoundVar(0)), List(Par()), false, 0, BitSet(0))), BitSet(0))
    val result = substitute(target)(env)
    result should be(
      New(1, Par(Send(Quote(source), List(Par()), false, 0, BitSet())), BitSet())
    )
  }

  "New" should "only substitute all Channels in body of express" in {
    val source0 = Par(GPrivate())
    val source1 = Par(GPrivate())
    val env     = HashMap(0 -> source0, 1 -> source1)
    val target = New(2,
                     Par(
                       Send(ChanVar(BoundVar(0)),
                            List(Par(Send(ChanVar(BoundVar(1)), List(Par()), false, 0, BitSet(1)))),
                            false,
                            0,
                            BitSet(0, 1))),
                     BitSet(0, 1))
    val result = substitute(target)(env)
    result should be(
      New(
        2,
        Par(
          Send(Quote(source0),
               List(Par(Send(Quote(source1), List(Par()), false, 0, BitSet()))),
               false,
               0,
               BitSet())
        ),
        BitSet()
      )
    )
  }
}