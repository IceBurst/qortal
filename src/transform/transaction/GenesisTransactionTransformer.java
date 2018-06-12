package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.TransactionData;
import data.transaction.GenesisTransactionData;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class GenesisTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int AMOUNT_LENGTH = LONG_LENGTH;
	// Note that Genesis transactions don't require reference, fee or signature:
	private static final int TYPELESS_LENGTH = TIMESTAMP_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new TransformationException("Byte data too short for GenesisTransaction");

		long timestamp = byteBuffer.getLong();
		String recipient = Serialization.deserializeRecipient(byteBuffer);
		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		return new GenesisTransactionData(recipient, amount, timestamp);
	}

	public static int getDataLength(TransactionData baseTransaction) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData baseTransaction) throws TransformationException {
		try {
			GenesisTransactionData transaction = (GenesisTransactionData) baseTransaction;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(transaction.getType().value));
			bytes.write(Longs.toByteArray(transaction.getTimestamp()));
			bytes.write(Base58.decode(transaction.getRecipient()));
			bytes.write(Serialization.serializeBigDecimal(transaction.getAmount()));

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData baseTransaction) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(baseTransaction);

		try {
			GenesisTransactionData transaction = (GenesisTransactionData) baseTransaction;

			json.put("recipient", transaction.getRecipient());
			json.put("amount", transaction.getAmount().toPlainString());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}