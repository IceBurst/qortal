package org.qortal.test.common.transaction;

import java.util.Random;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.block.BlockChain;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;

public abstract class TestTransaction {

	protected static final Random random = new Random();

	public static BaseTransactionData generateBase(PrivateKeyAccount account) throws DataException {
		byte[] lastReference = account.getUnconfirmedLastReference();
		if (lastReference == null)
			lastReference = account.getLastReference();

		return new BaseTransactionData(System.currentTimeMillis(), Group.NO_GROUP, lastReference, account.getPublicKey(), BlockChain.getInstance().getUnitFee(), null);
	}

}
