package org.qortal.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.group.GroupData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.UpdateGroupTransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import com.google.common.base.Utf8;

public class UpdateGroupTransaction extends Transaction {

	// Properties
	private UpdateGroupTransactionData updateGroupTransactionData;

	// Constructors

	public UpdateGroupTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.updateGroupTransactionData = (UpdateGroupTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(getNewOwner());
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getOwner().getAddress()))
			return true;

		if (address.equals(this.getNewOwner().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getOwner().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getOwner() throws DataException {
		return new PublicKeyAccount(this.repository, this.updateGroupTransactionData.getOwnerPublicKey());
	}

	public Account getNewOwner() throws DataException {
		return new Account(this.repository, this.updateGroupTransactionData.getNewOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check new owner address is valid
		if (!Crypto.isValidAddress(updateGroupTransactionData.getNewOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check new approval threshold is valid
		if (updateGroupTransactionData.getNewApprovalThreshold() == null)
			return ValidationResult.INVALID_GROUP_APPROVAL_THRESHOLD;

		// Check new description size bounds
		int newDescriptionLength = Utf8.encodedLength(updateGroupTransactionData.getNewDescription());
		if (newDescriptionLength < 1 || newDescriptionLength > Group.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		GroupData groupData = this.repository.getGroupRepository().fromGroupId(updateGroupTransactionData.getGroupId());

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		// As this transaction type could require approval, check txGroupId matches groupID at creation
		if (groupData.getCreationGroupId() != updateGroupTransactionData.getTxGroupId())
			return ValidationResult.TX_GROUP_ID_MISMATCH;

		Account owner = getOwner();

		// Check fee is positive
		if (updateGroupTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check creator has enough funds
		if (owner.getConfirmedBalance(Asset.QORT).compareTo(updateGroupTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		GroupData groupData = this.repository.getGroupRepository().fromGroupId(updateGroupTransactionData.getGroupId());
		Account owner = getOwner();

		// Check transaction's public key matches group's current owner
		if (!owner.getAddress().equals(groupData.getOwner()))
			return ValidationResult.INVALID_GROUP_OWNER;

		Account newOwner = getNewOwner();

		// Check new owner is not banned
		if (this.repository.getGroupRepository().banExists(updateGroupTransactionData.getGroupId(), newOwner.getAddress()))
			return ValidationResult.BANNED_FROM_GROUP;


		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group
		Group group = new Group(this.repository, updateGroupTransactionData.getGroupId());
		group.updateGroup(updateGroupTransactionData);

		// Save this transaction, now with updated "group reference" to previous transaction that updated group
		this.repository.getTransactionRepository().save(updateGroupTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert Group update
		Group group = new Group(this.repository, updateGroupTransactionData.getGroupId());
		group.unupdateGroup(updateGroupTransactionData);

		// Save this transaction, now with removed "group reference"
		this.repository.getTransactionRepository().save(updateGroupTransactionData);
	}

}
