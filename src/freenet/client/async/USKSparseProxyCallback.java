package freenet.client.async;

import com.db4o.ObjectContainer;

import freenet.keys.USK;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;

/** Proxy class to only pass through latest-slot updates after an onRoundFinished().
 * Note that it completely ignores last-known-good updates.
 * @author toad
 */
public class USKSparseProxyCallback implements USKProgressCallback {

	final USKCallback target;
	final USK key;

	private long lastEdition;
	private long lastSent;
	private boolean lastMetadata;
	private short lastCodec;
	private byte[] lastData;
	private boolean lastWasKnownGoodToo;
	private boolean roundFinished;
	
    private static volatile boolean logMINOR;
	static {
		Logger.registerClass(USKSparseProxyCallback.class);
	}

	public USKSparseProxyCallback(USKCallback cb, USK key) {
		target = cb;
		lastEdition = -1; // So we see the first one even if it's 0
		lastSent = -1;
		this.key = key;
		if(logMINOR) Logger.minor(this, "Creating sparse proxy callback "+this+" for "+cb+" for "+key);
	}

	public void onFoundEdition(long l, USK key, ObjectContainer container,
			ClientContext context, boolean metadata, short codec, byte[] data,
			boolean newKnownGood, boolean newSlotToo) {
		synchronized(this) {
			if(l < lastEdition) {
				if(!roundFinished) return;
				if(!newKnownGood) return;
			} else if(l == lastEdition) {
				if(newKnownGood) lastWasKnownGoodToo = true;
			} else {
				lastEdition = l;
				lastMetadata = metadata;
				lastCodec = codec;
				lastData = data;
				lastWasKnownGoodToo = newKnownGood;
			}
			if(!roundFinished) return;
		}
		target.onFoundEdition(l, key, null, context, metadata, codec, data, newKnownGood, newSlotToo);
	}

	public short getPollingPriorityNormal() {
		return target.getPollingPriorityNormal();
	}

	public short getPollingPriorityProgress() {
		return target.getPollingPriorityProgress();
	}

	public void onSendingToNetwork(ClientContext context) {
		innerRoundFinished(context, false);
	}

	public void onRoundFinished(ClientContext context) {
		innerRoundFinished(context, true);
	}
	
	private void innerRoundFinished(ClientContext context, boolean finishedRound) {
		long ed;
		boolean meta;
		short codec;
		byte[] data;
		boolean wasKnownGood;
		synchronized(this) {
			if(finishedRound)
				roundFinished = true;
			if(lastSent == lastEdition) return;
			lastSent = ed = lastEdition;
			meta = lastMetadata;
			codec = lastCodec;
			data = lastData;
			wasKnownGood = lastWasKnownGoodToo;
		}
		if(ed == -1) {
			ed = context.uskManager.lookupLatestSlot(key);
			if(ed == -1) return;
			meta = false;
			codec = -1;
			data = null;
			wasKnownGood = false;
		}
		if(ed == -1) return;
		target.onFoundEdition(ed, key, null, context, meta, codec, data, wasKnownGood, wasKnownGood);
	}

}
