package org.qortal.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.data.group.GroupData;
import org.qortal.data.transaction.LeaveGroupTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

public class LeaveGroupTransaction extends Transaction {

	// Properties
	private LeaveGroupTransactionData leaveGroupTransactionData;

	// Constructors

	public LeaveGroupTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.leaveGroupTransactionData = (LeaveGroupTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getLeaver().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getLeaver().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getLeaver() throws DataException {
		return new PublicKeyAccount(this.repository, this.leaveGroupTransactionData.getLeaverPublicKey());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		GroupData groupData = this.repository.getGroupRepository().fromGroupId(leaveGroupTransactionData.getGroupId());

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account leaver = getLeaver();

		// Can't leave if group owner
		if (leaver.getAddress().equals(groupData.getOwner()))
			return ValidationResult.GROUP_OWNER_CANNOT_LEAVE;

		// Check leaver is actually a member of group
		if (!this.repository.getGroupRepository().memberExists(leaveGroupTransactionData.getGroupId(), leaver.getAddress()))
			return ValidationResult.NOT_GROUP_MEMBER;

		// Check fee is positive
		if (leaveGroupTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check creator has enough funds
		if (leaver.getConfirmedBalance(Asset.QORT).compareTo(leaveGroupTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, leaveGroupTransactionData.getGroupId());
		group.leave(leaveGroupTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(leaveGroupTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, leaveGroupTransactionData.getGroupId());
		group.unleave(leaveGroupTransactionData);

		// Save this transaction with removed member/admin references
		this.repository.getTransactionRepository().save(leaveGroupTransactionData);
	}

}
