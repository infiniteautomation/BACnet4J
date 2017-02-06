package com.serotonin.bacnet4j.service.acknowledgement;

import com.serotonin.bacnet4j.util.sero.ByteQueue;

/**
 * Created by Tchuba on 4.4.2016.
 */
public class SimpleAckService extends AcknowledgementService {
    @Override
    public byte getChoiceId() {
        return -1;
    }

    @Override
    public void write(ByteQueue queue) {
    }
}
