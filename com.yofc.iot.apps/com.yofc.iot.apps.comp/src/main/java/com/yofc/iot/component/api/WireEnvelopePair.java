package com.yofc.iot.component.api;

import org.eclipse.kura.wire.WireEnvelope;

public class WireEnvelopePair {

    private WireEnvelope preWireEnvelope;
    private WireEnvelope currentWireEnvelope;
    private boolean hasElement;

    public boolean isHasElement() {
        return hasElement;
    }

    public void setHasElement(boolean hasElement) {
        this.hasElement = hasElement;
    }

    public WireEnvelopePair(WireEnvelope preWireEnvelope, WireEnvelope currentWireEnvelope, boolean hasElement) {
        super();
        if (preWireEnvelope == null)
            this.preWireEnvelope = currentWireEnvelope;
        else
            this.preWireEnvelope = preWireEnvelope;
        this.currentWireEnvelope = currentWireEnvelope;
        this.hasElement = hasElement;
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
