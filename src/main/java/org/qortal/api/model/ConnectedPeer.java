package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qortal.data.network.PeerChainTipData;
import org.qortal.data.network.PeerData;
import org.qortal.network.Handshake;
import org.qortal.network.Peer;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class ConnectedPeer {

	public enum Direction {
		INBOUND,
		OUTBOUND;
	}
	public Direction direction;
	public Handshake handshakeStatus;
	public Long lastPing;
	public Long connectedWhen;
	public Long peersConnectedWhen;

	public String address;
	public String version;
	public Long buildTimestamp;

	public Integer lastHeight;
	@Schema(example = "base58")
	public byte[] lastBlockSignature;
	public Long lastBlockTimestamp;

	protected ConnectedPeer() {
	}

	public ConnectedPeer(Peer peer) {
		this.direction = peer.isOutbound() ? Direction.OUTBOUND : Direction.INBOUND;
		this.handshakeStatus = peer.getHandshakeStatus();
		this.lastPing = peer.getLastPing();

		PeerData peerData = peer.getPeerData();
		this.connectedWhen = peer.getConnectionTimestamp();
		this.peersConnectedWhen = peer.getPeersConnectionTimestamp();

		this.address = peerData.getAddress().toString();
		if (peer.getVersionMessage() != null) {
			this.version = peer.getVersionMessage().getVersionString();
			this.buildTimestamp = peer.getVersionMessage().getBuildTimestamp();
		}

		PeerChainTipData peerChainTipData = peer.getChainTipData();
		if (peerChainTipData != null) {
			this.lastHeight = peerChainTipData.getLastHeight();
			this.lastBlockSignature = peerChainTipData.getLastBlockSignature();
			this.lastBlockTimestamp = peerChainTipData.getLastBlockTimestamp();
		}
	}

}
