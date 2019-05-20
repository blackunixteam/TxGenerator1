package org.encryfoundation.generator.network

import TransactionProto.TransactionProtoProto
import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.actors.Generator.TransactionForCommit
import org.encryfoundation.generator.network.BasicMessagesRepo.{ModifiersNetworkMessage, RequestModifiersNetworkMessage}
import org.encryfoundation.generator.network.NetworkMessagesHandler.BroadcastInvForTx
import org.encryfoundation.generator.modifiers.{Transaction, TransactionProtoSerializer, TransactionsFactory}
import org.encryfoundation.generator.utils.CoreTaggedTypes.{ModifierId, ModifierTypeId}
import org.encryfoundation.generator.utils.{CoreTaggedTypes, Settings}
import supertagged.@@

class NetworkMessagesHandler(settings: Settings) extends Actor with StrictLogging {

  var localGeneratedTransactions: Seq[Transaction] = Seq.empty

  override def receive: Receive = {
    case TransactionForCommit(transaction) =>
      localGeneratedTransactions :+= transaction
      context.parent ! BroadcastInvForTx(transaction)
    case RequestModifiersNetworkMessage(invData) if invData._1 == Transaction.modifierTypeId =>
      val tmpInv: Seq[String] = invData._2.map(Algos.encode)
      val transactions: Seq[Transaction] = localGeneratedTransactions.filter(tx => tmpInv.contains(Algos.encode(tx.id)))
      val forSend: Map[Array[Byte] @@ CoreTaggedTypes.ModifierId.Tag, Array[Byte]] = transactions.map { tx =>
        ModifierId @@ tx.id -> TransactionProtoSerializer.toProto(tx).toByteArray
      }.toMap
      sender() ! ModifiersNetworkMessage(ModifierTypeId @@ Transaction.modifierTypeId -> forSend)
      val tmpTxs = transactions.map(tx => Algos.encode(tx.id))
      localGeneratedTransactions = localGeneratedTransactions.filter(tx =>
        !tmpTxs.contains(Algos.encode(tx.id))
      )
    case msg => logger.info(s"Got $msg")
  }
}

object NetworkMessagesHandler {

  case class BroadcastInvForTx(tx: Transaction)

  def props(settings: Settings) = Props(new NetworkMessagesHandler(settings))
}