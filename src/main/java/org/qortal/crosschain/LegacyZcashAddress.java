/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Giannis Dzegoutanis
 * Copyright 2015 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Updated for Zcash in May 2022 by Qortal core dev team. Modifications allow
* correct encoding of P2SH (t3) addresses only. */

package org.qortal.crosschain;

import org.bitcoinj.core.*;
import org.bitcoinj.params.Networks;
import org.bitcoinj.script.Script.ScriptType;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

/**
 * <p>A Bitcoin address looks like 1MsScoe2fTJoq4ZPdQgqyhgWeoNamYPevy and is derived from an elliptic curve public key
 * plus a set of network parameters. Not to be confused with a {@link PeerAddress} or {@link AddressMessage}
 * which are about network (TCP) addresses.</p>
 *
 * <p>A standard address is built by taking the RIPE-MD160 hash of the public key bytes, with a version prefix and a
 * checksum suffix, then encoding it textually as base58. The version prefix is used to both denote the network for
 * which the address is valid (see {@link NetworkParameters}, and also to indicate how the bytes inside the address
 * should be interpreted. Whilst almost all addresses today are hashes of public keys, another (currently unsupported
 * type) can contain a hash of a script instead.</p>
 */
public class LegacyZcashAddress extends Address {
    /**
     * An address is a RIPEMD160 hash of a public key, therefore is always 160 bits or 20 bytes.
     */
    public static final int LENGTH = 20;

    /** True if P2SH, false if P2PKH. */
    public final boolean p2sh;

    /* Zcash P2SH header bytes */
    private static int P2SH_HEADER_1 = 28;
    private static int P2SH_HEADER_2 = 189;

    /**
     * Private constructor. Use {@link #fromBase58(NetworkParameters, String)},
     * {@link #fromPubKeyHash(NetworkParameters, byte[])}, {@link #fromScriptHash(NetworkParameters, byte[])} or
     * {@link #fromKey(NetworkParameters, ECKey)}.
     * 
     * @param params
     *            network this address is valid for
     * @param p2sh
     *            true if hash160 is hash of a script, false if it is hash of a pubkey
     * @param hash160
     *            20-byte hash of pubkey or script
     */
    private LegacyZcashAddress(NetworkParameters params, boolean p2sh, byte[] hash160) throws AddressFormatException {
        super(params, hash160);
        if (hash160.length != 20)
            throw new AddressFormatException.InvalidDataLength(
                    "Legacy addresses are 20 byte (160 bit) hashes, but got: " + hash160.length);
        this.p2sh = p2sh;
    }

    /**
     * Construct a {@link LegacyZcashAddress} that represents the given pubkey hash. The resulting address will be a P2PKH type of
     * address.
     * 
     * @param params
     *            network this address is valid for
     * @param hash160
     *            20-byte pubkey hash
     * @return constructed address
     */
    public static LegacyZcashAddress fromPubKeyHash(NetworkParameters params, byte[] hash160) throws AddressFormatException {
        return new LegacyZcashAddress(params, false, hash160);
    }

    /**
     * Construct a {@link LegacyZcashAddress} that represents the public part of the given {@link ECKey}. Note that an address is
     * derived from a hash of the public key and is not the public key itself.
     * 
     * @param params
     *            network this address is valid for
     * @param key
     *            only the public part is used
     * @return constructed address
     */
    public static LegacyZcashAddress fromKey(NetworkParameters params, ECKey key) {
        return fromPubKeyHash(params, key.getPubKeyHash());
    }

    /**
     * Construct a {@link LegacyZcashAddress} that represents the given P2SH script hash.
     * 
     * @param params
     *            network this address is valid for
     * @param hash160
     *            P2SH script hash
     * @return constructed address
     */
    public static LegacyZcashAddress fromScriptHash(NetworkParameters params, byte[] hash160) throws AddressFormatException {
        return new LegacyZcashAddress(params, true, hash160);
    }

    /**
     * Construct a {@link LegacyZcashAddress} from its base58 form.
     * 
     * @param params
     *            expected network this address is valid for, or null if if the network should be derived from the
     *            base58
     * @param base58
     *            base58-encoded textual form of the address
     * @throws AddressFormatException
     *             if the given base58 doesn't parse or the checksum is invalid
     * @throws AddressFormatException.WrongNetwork
     *             if the given address is valid but for a different chain (eg testnet vs mainnet)
     */
    public static LegacyZcashAddress fromBase58(@Nullable NetworkParameters params, String base58)
            throws AddressFormatException, AddressFormatException.WrongNetwork {
        byte[] versionAndDataBytes = Base58.decodeChecked(base58);
        int version = versionAndDataBytes[0] & 0xFF;
        byte[] bytes = Arrays.copyOfRange(versionAndDataBytes, 1, versionAndDataBytes.length);
        if (params == null) {
            for (NetworkParameters p : Networks.get()) {
                if (version == p.getAddressHeader())
                    return new LegacyZcashAddress(p, false, bytes);
                else if (version == p.getP2SHHeader())
                    return new LegacyZcashAddress(p, true, bytes);
            }
            throw new AddressFormatException.InvalidPrefix("No network found for " + base58);
        } else {
            if (version == params.getAddressHeader())
                return new LegacyZcashAddress(params, false, bytes);
            else if (version == params.getP2SHHeader())
                return new LegacyZcashAddress(params, true, bytes);
            throw new AddressFormatException.WrongNetwork(version);
        }
    }

    /**
     * Get the version header of an address. This is the first byte of a base58 encoded address.
     * 
     * @return version header as one byte
     */
    public int getVersion() {
        return p2sh ? params.getP2SHHeader() : params.getAddressHeader();
    }

    /**
     * Returns the base58-encoded textual form, including version and checksum bytes.
     * 
     * @return textual form
     */
    public String toBase58() {
        return this.encodeChecked(getVersion(), bytes);
    }

    /** The (big endian) 20 byte hash that is the core of a Bitcoin address. */
    @Override
    public byte[] getHash() {
        return bytes;
    }

    /**
     * Get the type of output script that will be used for sending to the address. This is either
     * {@link ScriptType#P2PKH} or {@link ScriptType#P2SH}.
     * 
     * @return type of output script
     */
    @Override
    public ScriptType getOutputScriptType() {
        return p2sh ? ScriptType.P2SH : ScriptType.P2PKH;
    }

    @Override
    public int compareTo(Address address) {
        return this.toString().compareTo(address.toString());
    }

    /**
     * Given an address, examines the version byte and attempts to find a matching NetworkParameters. If you aren't sure
     * which network the address is intended for (eg, it was provided by a user), you can use this to decide if it is
     * compatible with the current wallet.
     * 
     * @return network the address is valid for
     * @throws AddressFormatException if the given base58 doesn't parse or the checksum is invalid
     */
    public static NetworkParameters getParametersFromAddress(String address) throws AddressFormatException {
        return LegacyZcashAddress.fromBase58(null, address).getParameters();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LegacyZcashAddress other = (LegacyZcashAddress) o;
        return super.equals(other) && this.p2sh == other.p2sh;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), p2sh);
    }

    @Override
    public String toString() {
        return toBase58();
    }

    @Override
    public LegacyZcashAddress clone() throws CloneNotSupportedException {
        return (LegacyZcashAddress) super.clone();
    }

    public static String encodeChecked(int version, byte[] payload) {
        if (version < 0 || version > 255)
            throw new IllegalArgumentException("Version not in range.");

        // A stringified buffer is:
        // 1 byte version + data bytes + 4 bytes check code (a truncated hash)
        byte[] addressBytes = new byte[2 + payload.length + 4];
        addressBytes[0] = (byte) P2SH_HEADER_1;
        addressBytes[1] = (byte) P2SH_HEADER_2;
        System.arraycopy(payload, 0, addressBytes, 2, payload.length);
        byte[] checksum = Sha256Hash.hashTwice(addressBytes, 0, payload.length + 2);
        System.arraycopy(checksum, 0, addressBytes, payload.length + 2, 4);
        return Base58.encode(addressBytes);
    }

//    // Comparator for LegacyAddress, left argument must be LegacyAddress, right argument can be any Address
//    private static final Comparator<Address> LEGACY_ADDRESS_COMPARATOR = Address.PARTIAL_ADDRESS_COMPARATOR
//            .thenComparingInt(a -> ((LegacyZcashAddress) a).getVersion())                    // Then compare Legacy address version byte
//            .thenComparing(a -> a.bytes, UnsignedBytes.lexicographicalComparator());    // Then compare Legacy bytes
//
//    /**
//     * {@inheritDoc}
//     *
//     * @param o other {@code Address} object
//     * @return comparison result
//     */
//    @Override
//    public int compareTo(Address o) {
//       return LEGACY_ADDRESS_COMPARATOR.compare(this, o);
//    }
}
