package org.qortal.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qortal.block.BlockChain;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CreatePollTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;
import org.qortal.voting.Poll;

import com.google.common.base.Utf8;
import com.google.common.primitives.Ints;

public class CreatePollTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int OPTIONS_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH + OPTIONS_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.CREATE_POLL.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("poll creator's public key", TransformationType.PUBLIC_KEY);
		layout.add("poll name length", TransformationType.INT);
		layout.add("poll name", TransformationType.STRING);
		layout.add("poll description length", TransformationType.INT);
		layout.add("poll description", TransformationType.STRING);
		layout.add("number of options", TransformationType.INT);
		layout.add("* poll option length", TransformationType.INT);
		layout.add("* poll option", TransformationType.STRING);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = 0;
		if (timestamp >= BlockChain.getInstance().getQortalTimestamp())
			txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String owner = Serialization.deserializeAddress(byteBuffer);

		String pollName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

		String description = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_DESCRIPTION_SIZE);

		int optionsCount = byteBuffer.getInt();
		if (optionsCount < 1 || optionsCount > Poll.MAX_OPTIONS)
			throw new TransformationException("Invalid number of options for CreatePollTransaction");

		List<PollOptionData> pollOptions = new ArrayList<>();
		for (int optionIndex = 0; optionIndex < optionsCount; ++optionIndex) {
			String optionName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

			pollOptions.add(new PollOptionData(optionName));

			// V1 only: voter count also present
			if (timestamp < BlockChain.getInstance().getQortalTimestamp()) {
				int voterCount = byteBuffer.getInt();
				if (voterCount != 0)
					throw new TransformationException("Unexpected voter count in byte data for CreatePollTransaction");
			}
		}

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, signature);

		return new CreatePollTransactionData(baseTransactionData, owner, pollName, description, pollOptions);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

		int dataLength = getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(createPollTransactionData.getPollName())
				+ Utf8.encodedLength(createPollTransactionData.getDescription());

		// Add lengths for each poll options
		for (PollOptionData pollOptionData : createPollTransactionData.getPollOptions()) {
			// option-string-length, option-string
			dataLength += INT_LENGTH + Utf8.encodedLength(pollOptionData.getOptionName());

			if (transactionData.getTimestamp() < BlockChain.getInstance().getQortalTimestamp())
				// v1 only: voter-count (should always be zero)
				dataLength += INT_LENGTH;
		}

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, createPollTransactionData.getOwner());

			Serialization.serializeSizedString(bytes, createPollTransactionData.getPollName());

			Serialization.serializeSizedString(bytes, createPollTransactionData.getDescription());

			List<PollOptionData> pollOptions = createPollTransactionData.getPollOptions();
			bytes.write(Ints.toByteArray(pollOptions.size()));

			for (PollOptionData pollOptionData : pollOptions) {
				Serialization.serializeSizedString(bytes, pollOptionData.getOptionName());

				if (transactionData.getTimestamp() < BlockChain.getInstance().getQortalTimestamp()) {
					// In v1, CreatePollTransaction uses Poll.toBytes which serializes voters too.
					// Zero voters as this is a new poll.
					bytes.write(Ints.toByteArray(0));
				}
			}

			Serialization.serializeBigDecimal(bytes, createPollTransactionData.getFee());

			if (createPollTransactionData.getSignature() != null)
				bytes.write(createPollTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	/**
	 * In Qora v1, the bytes used for verification have transaction type set to REGISTER_NAME_TRANSACTION so we need to test for v1-ness and adjust the bytes
	 * accordingly.
	 * 
	 * @param transactionData
	 * @return byte[]
	 * @throws TransformationException
	 */
	public static byte[] toBytesForSigningImpl(TransactionData transactionData) throws TransformationException {
		byte[] bytes = TransactionTransformer.toBytesForSigningImpl(transactionData);

		if (transactionData.getTimestamp() >= BlockChain.getInstance().getQortalTimestamp())
			return bytes;

		// Special v1 version

		// Replace transaction type with incorrect Register Name value
		System.arraycopy(Ints.toByteArray(TransactionType.REGISTER_NAME.value), 0, bytes, 0, INT_LENGTH);

		return bytes;
	}

}
