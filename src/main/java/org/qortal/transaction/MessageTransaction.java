package org.qortal.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.data.PaymentData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.payment.Payment;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

public class MessageTransaction extends Transaction {

	// Properties
	private MessageTransactionData messageTransactionData;

	// Other useful constants
	public static final int MAX_DATA_SIZE = 4000;

	// Constructors

	public MessageTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.messageTransactionData = (MessageTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(new Account(this.repository, messageTransactionData.getRecipient()));
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getSender().getAddress()))
			return true;

		if (address.equals(messageTransactionData.getRecipient()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);
		String senderAddress = this.getSender().getAddress();

		if (address.equals(senderAddress))
			amount = amount.subtract(this.transactionData.getFee());

		// We're only interested in QORT
		if (messageTransactionData.getAssetId() == Asset.QORT) {
			if (address.equals(messageTransactionData.getRecipient()))
				amount = amount.add(messageTransactionData.getAmount());
			else if (address.equals(senderAddress))
				amount = amount.subtract(messageTransactionData.getAmount());
		}

		return amount;
	}

	// Navigation

	public Account getSender() throws DataException {
		return new PublicKeyAccount(this.repository, this.messageTransactionData.getSenderPublicKey());
	}

	public Account getRecipient() throws DataException {
		return new Account(this.repository, this.messageTransactionData.getRecipient());
	}

	// Processing

	private PaymentData getPaymentData() {
		return new PaymentData(messageTransactionData.getRecipient(), messageTransactionData.getAssetId(), messageTransactionData.getAmount());
	}

	@Override
	public ValidationResult isValid() throws DataException {
		// Are message transactions even allowed at this point?
		if (messageTransactionData.getVersion() != Transaction.getVersionByTimestamp(messageTransactionData.getTimestamp()))
			return ValidationResult.NOT_YET_RELEASED;

		if (this.repository.getBlockRepository().getBlockchainHeight() < BlockChain.getInstance().getMessageReleaseHeight())
			return ValidationResult.NOT_YET_RELEASED;

		// Check data length
		if (messageTransactionData.getData().length < 1 || messageTransactionData.getData().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Zero-amount payments (i.e. message-only) only valid for versions later than 1
		boolean isZeroAmountValid = messageTransactionData.getVersion() > 1;

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				isZeroAmountValid);
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Zero-amount payments (i.e. message-only) only valid for versions later than 1
		boolean isZeroAmountValid = messageTransactionData.getVersion() > 1;

		// Wrap and delegate final processable checks to Payment class
		return new Payment(this.repository).isProcessable(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				isZeroAmountValid);
	}

	@Override
	public void process() throws DataException {
		// Wrap and delegate payment processing to Payment class.
		new Payment(this.repository).process(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				messageTransactionData.getSignature());
	}

	@Override
	public void processReferencesAndFees() throws DataException {
		// Wrap and delegate references processing to Payment class. Only update recipient's last reference if transferring QORT.
		new Payment(this.repository).processReferencesAndFees(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				messageTransactionData.getSignature(), false);
	}

	@Override
	public void orphan() throws DataException {
		// Wrap and delegate payment processing to Payment class.
		new Payment(this.repository).orphan(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				messageTransactionData.getSignature(), messageTransactionData.getReference());
	}

	@Override
	public void orphanReferencesAndFees() throws DataException {
		// Wrap and delegate references processing to Payment class. Only revert recipient's last reference if transferring QORT.
		new Payment(this.repository).orphanReferencesAndFees(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				messageTransactionData.getSignature(), messageTransactionData.getReference(), false);
	}

}
