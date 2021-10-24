package org.qortal.test.arbitrary;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.ArbitraryDataDigest;
import org.qortal.arbitrary.ArbitraryDataFile.*;
import org.qortal.arbitrary.ArbitraryDataReader;
import org.qortal.arbitrary.ArbitraryDataTransactionBuilder;
import org.qortal.arbitrary.metadata.ArbitraryDataMetadataPatch;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.ArbitraryTransactionData.*;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.Transaction;
import org.qortal.utils.Base58;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class ArbitraryDataTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @Test
    public void testCombineMultipleLayers() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST";
            Service service = Service.WEBSITE;

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
            this.createAndMintTxn(repository, publicKey58, path1, name, Method.PUT, service, alice);

            // Create PATCH transaction
            Path path2 = Paths.get("src/test/resources/arbitrary/demo2");
            this.createAndMintTxn(repository, publicKey58, path2, name, Method.PATCH, service, alice);

            // Create another PATCH transaction
            Path path3 = Paths.get("src/test/resources/arbitrary/demo3");
            this.createAndMintTxn(repository, publicKey58, path3, name, Method.PATCH, service, alice);

            // Now build the latest data state for this name
            ArbitraryDataReader arbitraryDataReader = new ArbitraryDataReader(name, ResourceIdType.NAME, service);
            arbitraryDataReader.loadSynchronously(true);
            Path finalPath = arbitraryDataReader.getFilePath();

            // Ensure it exists
            assertTrue(Files.exists(finalPath));

            // Its directory hash should match the hash of demo3
            ArbitraryDataDigest path3Digest = new ArbitraryDataDigest(path3);
            path3Digest.compute();
            ArbitraryDataDigest finalPathDigest = new ArbitraryDataDigest(finalPath);
            finalPathDigest.compute();
            assertEquals(path3Digest.getHash58(), finalPathDigest.getHash58());

            // .. and its directory hash should also match the one included in the metadata
            ArbitraryDataMetadataPatch patchMetadata = new ArbitraryDataMetadataPatch(finalPath);
            patchMetadata.read();
            assertArrayEquals(patchMetadata.getCurrentHash(), path3Digest.getHash());

        }
    }

    private void createAndMintTxn(Repository repository, String publicKey58, Path path, String name,
                                  Method method, Service service, PrivateKeyAccount account) throws DataException {

        ArbitraryDataTransactionBuilder txnBuilder = new ArbitraryDataTransactionBuilder(publicKey58, path, name, method, service);
        ArbitraryTransactionData transactionData = txnBuilder.build();
        Transaction.ValidationResult result = TransactionUtils.signAndImport(repository, transactionData, account);
        assertEquals(Transaction.ValidationResult.OK, result);
        BlockUtils.mintBlock(repository);
    }

}
