package com.yofc.iot.component.api;

import org.eclipse.kura.wire.WireEnvelope;

public class WireEnvelopePair {

    private WireEnvelope preWireEnvelope;
    private WireEnvelope currentWireEnvelope;

    public WireEnvelopePair(WireEnvelope preWireEnvelope, WireEnvelope currentWireEnvelope) {
        super();
        this.preWireEnvelope = preWireEnvelope;
        this.currentWireEnvelope = currentWireEnvelope;
    }

    public WireEnvelope getPreWireEnvelope() {
        return preWireEnvelope;
    }

    public void setPreWireEnvelope(WireEnvelope preWireEnvelope) {
        this.preWireEnvelope = preWireEnvelope;
    }

    public WireEnvelope getCurrentWireEnvelope() {
        return currentWireEnvelope;
    }

    public void setCurrentWireEnvelope(WireEnvelope currentWireEnvelope) {
        this.currentWireEnvelope = currentWireEnvelope;
    }

}
