/*
 * ============================================================================
 * GNU General Public License
 * ============================================================================
 *
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * When signing a commercial license with Infinite Automation Software,
 * the following extension to GPL is made. A special exception to the GPL is
 * included to allow you to distribute a combined work that includes BAcnet4J
 * without being obliged to provide the source code for any proprietary components.
 *
 * See www.infiniteautomation.com for commercial license options.
 * 
 * @author Matthew Lohbihler
 */
package com.serotonin.bacnet4j.transport;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.serotonin.bacnet4j.service.acknowledgement.SimpleAckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.ResponseConsumer;
import com.serotonin.bacnet4j.ServiceFuture;
import com.serotonin.bacnet4j.apdu.APDU;
import com.serotonin.bacnet4j.apdu.Abort;
import com.serotonin.bacnet4j.apdu.AckAPDU;
import com.serotonin.bacnet4j.apdu.ComplexACK;
import com.serotonin.bacnet4j.apdu.ConfirmedRequest;
import com.serotonin.bacnet4j.apdu.Reject;
import com.serotonin.bacnet4j.apdu.SegmentACK;
import com.serotonin.bacnet4j.apdu.Segmentable;
import com.serotonin.bacnet4j.apdu.SimpleACK;
import com.serotonin.bacnet4j.apdu.UnconfirmedRequest;
import com.serotonin.bacnet4j.enums.MaxSegments;
import com.serotonin.bacnet4j.exception.BACnetErrorException;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetRejectException;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.exception.BACnetTimeoutException;
import com.serotonin.bacnet4j.exception.NotImplementedException;
import com.serotonin.bacnet4j.exception.ServiceTooBigException;
import com.serotonin.bacnet4j.npdu.NPDU;
import com.serotonin.bacnet4j.npdu.Network;
import com.serotonin.bacnet4j.npdu.NetworkIdentifier;
import com.serotonin.bacnet4j.service.acknowledgement.AcknowledgementService;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedRequestService;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedRequestService;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.BACnetError;
import com.serotonin.bacnet4j.type.constructed.ServicesSupported;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.Segmentation;
import com.serotonin.bacnet4j.type.error.BaseError;
import com.serotonin.bacnet4j.type.primitive.OctetString;
import com.serotonin.bacnet4j.util.sero.ByteQueue;
import com.serotonin.bacnet4j.util.sero.ThreadUtils;

/**
 * @author Matthew
 */
public class DefaultTransport implements Transport, Runnable {
    static final Logger LOG = LoggerFactory.getLogger(DefaultTransport.class);
    static final MaxSegments MAX_SEGMENTS = MaxSegments.MORE_THAN_64;

    final Map<Integer, OctetString> networkRouters = new HashMap<Integer, OctetString>();

    // Configuration
    private LocalDevice localDevice;
    final Network network;
    int timeout = DEFAULT_TIMEOUT;
    int retries = DEFAULT_RETRIES;
    int segTimeout = DEFAULT_SEG_TIMEOUT;
    int segWindow = DEFAULT_SEG_WINDOW;
    ServicesSupported servicesSupported;

    // Message queues
    private final Queue<Outgoing> outgoing = new ConcurrentLinkedQueue<Outgoing>();
    private final Queue<NPDU> incoming = new ConcurrentLinkedQueue<NPDU>();

    // Processing
    final UnackedMessages unackedMessages = new UnackedMessages();
    private Thread thread;
    private volatile boolean running = true;
    private final Object pauseLock = new Object();

    public DefaultTransport(Network network) {
        this.network = network;
    }

    //
    //
    // Configuration
    //
    @Override
    public NetworkIdentifier getNetworkIdentifier() {
        return network.getNetworkIdentifier();
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public void setSegTimeout(int segTimeout) {
        this.segTimeout = segTimeout;
    }

    @Override
    public int getSegTimeout() {
        return segTimeout;
    }

    @Override
    public void setRetries(int retries) {
        this.retries = retries;
    }

    @Override
    public int getRetries() {
        return retries;
    }

    @Override
    public void setSegWindow(int segWindow) {
        this.segWindow = segWindow;
    }

    @Override
    public int getSegWindow() {
        return segWindow;
    }

    @Override
    public Network getNetwork() {
        return network;
    }

    @Override
    public LocalDevice getLocalDevice() {
        return localDevice;
    }

    @Override
    public void setLocalDevice(LocalDevice localDevice) {
        this.localDevice = localDevice;
    }

    @Override
    public void initialize() throws Exception {
        try {
            servicesSupported = localDevice.getServicesSupported();
        }
        catch (BACnetServiceException e) {
            throw new RuntimeException("Error while getting servicesSupported from local device", e);
        }

        network.initialize(this);
        thread = new Thread(this, "BACnet4J transport");
        thread.start();

        // Send a WhoIsRouter message.
        network.sendNetworkMessage(getLocalBroadcastAddress(), null, 0, null, true, false);
    }

    @Override
    public void terminate() {
        running = false;
        ThreadUtils.notifySync(pauseLock);
        if (thread != null)
            ThreadUtils.join(thread);
        network.terminate();
    }

    @Override
    public long getBytesOut() {
        return network.getBytesOut();
    }

    @Override
    public long getBytesIn() {
        return network.getBytesIn();
    }

    @Override
    public Address getLocalBroadcastAddress() {
        return network.getLocalBroadcastAddress();
    }

    @Override
    public void addNetworkRouter(int networkNumber, OctetString mac) {
        networkRouters.put(networkNumber, mac);
    }

    @Override
    public Map<Integer, OctetString> getNetworkRouters() {
        return networkRouters;
    }

    //
    //
    // Adding new requests and responses.
    //
    @Override
    public void send(Address address, UnconfirmedRequestService service, boolean broadcast) {
        outgoing.add(new OutgoingUnconfirmed(address, service, broadcast));
        ThreadUtils.notifySync(pauseLock);
    }

    @Override
    public ServiceFuture send(Address address, int maxAPDULengthAccepted, Segmentation segmentationSupported,
            ConfirmedRequestService service) {
        if (Thread.currentThread() == thread)
            throw new IllegalStateException("Cannot send future request in the transport thread. Use a callback " // 
                    + "call instead, or make this call in a new thread.");
        ServiceFutureImpl future = new ServiceFutureImpl(timeout);
        send(address, maxAPDULengthAccepted, segmentationSupported, service, future);
        return future;
    }

    @Override
    public void send(Address address, int maxAPDULengthAccepted, Segmentation segmentationSupported,
            ConfirmedRequestService service, ResponseConsumer consumer) {
        outgoing.add(new OutgoingConfirmed(address, maxAPDULengthAccepted, segmentationSupported, service, consumer));
        ThreadUtils.notifySync(pauseLock);
    }

    @Override
    public void incoming(NPDU npdu) {
        incoming.add(npdu);
        ThreadUtils.notifySync(pauseLock);
    }

    abstract class Outgoing {
        protected final Address address;
        protected OctetString linkService;

        public Outgoing(Address address) {
            if (address == null)
                throw new IllegalArgumentException("address cannot be null");
            this.address = address;
        }

        void send() {
            try {
                // Check if the message is to be sent to a specific remote network.
                int targetNetworkNumber = address.getNetworkNumber().intValue();
                if (targetNetworkNumber != Address.LOCAL_NETWORK && targetNetworkNumber != Address.ALL_NETWORKS
                        && targetNetworkNumber != network.getLocalNetworkNumber()) {
                    // Going to a specific remote network. Check if we know the router for it.
                    linkService = networkRouters.get(targetNetworkNumber);
                    if (linkService == null)
                        handleException(new BACnetTimeoutException(
                                "Unable to find router to network " + address.getNetworkNumber().intValue()));
                }
                sendImpl();
            }
            catch (BACnetException e) {
                handleException(e);
            }
        }

        abstract protected void sendImpl() throws BACnetException;

        abstract protected void handleException(BACnetException e);
    }

    class OutgoingConfirmed extends Outgoing {
        private final int maxAPDULengthAccepted;
        private final Segmentation segmentationSupported;
        private final ConfirmedRequestService service;
        private final ResponseConsumer consumer;

        public OutgoingConfirmed(Address address, int maxAPDULengthAccepted, Segmentation segmentationSupported,
                ConfirmedRequestService service, ResponseConsumer consumer) {
            super(address);
            this.maxAPDULengthAccepted = maxAPDULengthAccepted;
            this.segmentationSupported = segmentationSupported;
            this.service = service;
            this.consumer = consumer;
        }

        @Override
        protected void sendImpl() throws BACnetException {
            ByteQueue serviceData = new ByteQueue();
            service.write(serviceData);

            UnackedMessageContext ctx = new UnackedMessageContext(timeout, retries, consumer);
            UnackedMessageKey key = unackedMessages.addClient(address, linkService, ctx);

            APDU apdu;

            // Check if we need to segment the message.
            if (serviceData.size() > maxAPDULengthAccepted - ConfirmedRequest.getHeaderSize(false)) {
                int maxServiceData = maxAPDULengthAccepted - ConfirmedRequest.getHeaderSize(true);
                // Check if the device can accept what we want to send.
                if (segmentationSupported.intValue() == Segmentation.noSegmentation.intValue()
                        || segmentationSupported.intValue() == Segmentation.segmentedTransmit.intValue())
                    throw new ServiceTooBigException("Request too big to send to device without segmentation");
                int segmentsRequired = serviceData.size() / maxServiceData + 1;
                if (segmentsRequired > 128)
                    throw new ServiceTooBigException("Request too big to send to device; too many segments required");

                // Prepare the segmenting session.
                ctx.setSegmentTemplate(new ConfirmedRequest(true, true, true, MAX_SEGMENTS, network.getMaxApduLength(),
                        key.getInvokeId(), 0, segWindow, service.getChoiceId(), null, service.getNetworkPriority()));
                ctx.setServiceData(serviceData);
                ctx.setSegBuf(new byte[maxServiceData]);

                // Send an initial message to negotiate communication terms.
                apdu = ctx.getSegmentTemplate().clone(true, 0, segWindow, ctx.getNextSegment());
            }
            else
                // We can send the whole APDU in one shot.
                apdu = new ConfirmedRequest(false, false, true, MAX_SEGMENTS, network.getMaxApduLength(),
                        key.getInvokeId(), (byte) 0, 0, service.getChoiceId(), serviceData,
                        service.getNetworkPriority());

            ctx.setOriginalApdu(apdu);
            sendForResponse(key, ctx);
        }

        @Override
        protected void handleException(BACnetException e) {
            consumer.ex(e);
        }
    }

    class OutgoingUnconfirmed extends Outgoing {
        private final UnconfirmedRequestService service;
        private final boolean broadcast;

        public OutgoingUnconfirmed(Address address, UnconfirmedRequestService service, boolean broadcast) {
            super(address);
            this.service = service;
            this.broadcast = broadcast;
        }

        @Override
        protected void sendImpl() throws BACnetException {
            network.sendAPDU(address, linkService, new UnconfirmedRequest(service), broadcast);
        }

        @Override
        protected void handleException(BACnetException e) {
            LOG.error("Error during send", e);
        }
    }

    //
    //
    // Processing
    //
    @Override
    public void run() {
        Outgoing out;
        NPDU in;
        boolean pause;

        while (running) {
            pause = true;

            // Send an outgoing message.
            out = outgoing.poll();
            if (out != null) {
                try {
                    out.send();
                }
                catch (Exception e) {
                    LOG.error("Error during send: {}", out, e);
                }
                pause = false;
            }

            // Receive an incoming message.
            in = incoming.poll();
            if (in != null) {
                try {
                    receiveImpl(in);
                }
                catch (Exception e) {
                    LOG.error("Error during receive: {}", in, e);
                }
                pause = false;
            }

            if (pause && running){
            	try{
            		pause = expire();
            	}catch(Exception e){
            		LOG.error("Error during expire messages: ", e);
            	}
            }

            if (pause && running)
                ThreadUtils.waitSync(pauseLock, 50);
        }
    }

    private void receiveImpl(NPDU in) {
        if (in.isNetworkMessage()) {
            switch (in.getNetworkMessageType()) {
            case 0x1: // I-Am-Router-To-Network
            case 0x2: // I-Could-Be-Router-To-Network
                ByteQueue data = in.getNetworkMessageData();
                while (data.size() > 1)
                    networkRouters.put(data.popU2B(), in.getFrom().getMacAddress());
                break;
            case 0x3: // Reject-Message-To-Network
                String reason;
                int reasonCode = in.getNetworkMessageData().popU1B();
                if (reasonCode == 0)
                    reason = "Other error";
                else if (reasonCode == 1)
                    reason = "The router is not directly connected to DNET and cannot find a router to DNET on any " //
                            + "directly connected network using Who-Is-Router-To-Network messages.";
                else if (reasonCode == 2)
                    reason = "The router is busy and unable to accept messages for the specified DNET at the " //
                            + "present time.";
                else if (reasonCode == 3)
                    reason = "It is an unknown network layer message type. The DNET returned in this case is a " //
                            + "local matter.";
                else if (reasonCode == 4)
                    reason = "The message is too long to be routed to this DNET.";
                else if (reasonCode == 5)
                    reason = "The source message was rejected due to a BACnet security error and that error cannot " //
                            + " be forwarded to the source device. See Clause 24.12.1.1 for more details on the " //
                            + "generation of Reject-Message-To-Network messages indicating this reason.";
                else if (reasonCode == 6)
                    reason = "The source message was rejected due to errors in the addressing. The length of the " //
                            + "DADR or SADR was determined to be invalid.";
                else
                    reason = "Unknown reason code";
                LOG.warn("Received Reject-Message-To-Network with reason '{}': {}", reasonCode, reason);
            }
        }
        else
            receiveAPDU(in);
    }

    private void receiveAPDU(NPDU npdu) {
        Address from = npdu.getFrom();
        OctetString linkService = npdu.getLinkService();
        APDU apdu;

        try {
            apdu = npdu.getAPDU(servicesSupported);
        }
        catch (BACnetException e) {
            // Error parsing the APDU. Drop the request.
            return;
        }

        if (apdu instanceof ConfirmedRequest) {
            // Received a request that must be handled and responded to.
            final ConfirmedRequest confAPDU = (ConfirmedRequest) apdu;
            final byte invokeId = confAPDU.getInvokeId();

            try {
                ConfirmedRequestService.checkConfirmedRequestService(servicesSupported, confAPDU.getServiceChoice());
            }
            catch (BACnetErrorException e) {
                com.serotonin.bacnet4j.apdu.Error error = new com.serotonin.bacnet4j.apdu.Error(confAPDU.getInvokeId(),
                        e.getError());
                try {
                    network.sendAPDU(from, linkService, error, false);
                }
                catch (BACnetException e1) {
                    LOG.warn("Error sending error response", e);
                }
                localDevice.getExceptionDispatcher().fireReceivedException(e);
                return;
            }

            if (confAPDU.isSegmentedMessage()) {
                UnackedMessageKey key = new UnackedMessageKey(from, linkService, invokeId, false);
                UnackedMessageContext ctx;
                if (confAPDU.getSequenceNumber() == 0)
                    // This is the first segment
                    ctx = new UnackedMessageContext(timeout, retries, null);
                else {
                    ctx = unackedMessages.remove(key);
                    if (ctx == null)
                        LOG.warn("Received an request segment for an unknown request: {}", confAPDU);
                }

                try {
                    segmentedIncoming(key, confAPDU, ctx);
                }
                catch (BACnetException e) {
                    LOG.warn("Error handling incoming request", e);
                    com.serotonin.bacnet4j.apdu.Error error = new com.serotonin.bacnet4j.apdu.Error(
                            confAPDU.getInvokeId(), new BaseError((byte) 127,
                                    new BACnetError(ErrorClass.services, ErrorCode.operationalProblem)));
                    try {
                        network.sendAPDU(from, linkService, error, false);
                    }
                    catch (BACnetException e1) {
                        LOG.warn("Error sending error response", e);
                    }
                    localDevice.getExceptionDispatcher().fireReceivedException(e);
                }
            }
            else
                // Just handle the message.
                incomingConfirmedRequest(confAPDU, from, linkService, invokeId);
        }
        else if (apdu instanceof UnconfirmedRequest) {
            // Received a request that must be handled with no response.
            UnconfirmedRequest ur = (UnconfirmedRequest) apdu;

            try {
                ur.parseServiceData();
                ur.getService().handle(localDevice, from);
            }
            catch (BACnetRejectException e) {
                // Ignore
            }
            catch (BACnetException e) {
                localDevice.getExceptionDispatcher().fireReceivedException(e);
            }
        }
        else {
            // Must be an acknowledgement
            LOG.debug("incomingApdu: recieved an acknowledgement");

            AckAPDU ack = (AckAPDU) apdu;
            UnackedMessageKey key = new UnackedMessageKey(from, linkService, ack.getOriginalInvokeId(), ack.isServer());
            UnackedMessageContext ctx = unackedMessages.remove(key);

            if (ctx == null)
                LOG.warn("Received an acknowledgement for an unknown request: {}", ack);
            else if (ack instanceof SegmentACK)
                segmentedOutgoing(key, ctx, (SegmentACK) ack);
            else if (ctx.getConsumer() != null) {
                ResponseConsumer consumer = ctx.getConsumer();

                if (ack instanceof SimpleACK)
                    consumer.success(new SimpleAckService());
                else if (ack instanceof ComplexACK) {
                    ComplexACK cack = ((ComplexACK) ack);
                    if (cack.isSegmentedMessage()) {
                        try {
                            segmentedIncoming(key, cack, ctx);
                        }
                        catch (BACnetException e) {
                            consumer.ex(e);
                        }
                    }
                    else
                        completeComplexAckResponse(cack, consumer);
                }
                else if (ack instanceof com.serotonin.bacnet4j.apdu.Error)
                    consumer.fail(ack);
                else if (ack instanceof Reject)
                    consumer.fail(ack);
                else if (ack instanceof Abort)
                    consumer.fail(ack);
                else
                    LOG.error("Unexpected ack APDU: " + ack);
            }
        }
    }

    private void segmentedIncoming(UnackedMessageKey key, Segmentable msg, UnackedMessageContext ctx)
            throws BACnetException {
        int windowSize = msg.getProposedWindowSize();
        int lastSeq = msg.getSequenceNumber() & 0xff;

        if (ctx.getSegmentWindow() == null) {
            // This is the first segment.
            ctx.setSegmentWindow(new SegmentWindow(windowSize, lastSeq + 1));
            ctx.setSegmentedMessage(msg);

            // Send a segment acknowledgement going with the proposed window size.
            network.sendAPDU(key.getAddress(), key.getLinkService(),
                    new SegmentACK(false, !key.isFromServer(), msg.getInvokeId(), lastSeq, windowSize, true), false);
        }
        else {
            SegmentWindow segmentWindow = ctx.getSegmentWindow();

            if (!segmentWindow.fitsInWindow(msg))
                throw new BACnetException("Segment did not fit in segment window");
            segmentWindow.setSegment(msg);

            // Do we need to send an ack?
            if (!msg.isMoreFollows() || segmentWindow.isFull()) {
                // Send an acknowledgement
                network.sendAPDU(key.getAddress(), key.getLinkService(), new SegmentACK(false, !key.isFromServer(),
                        msg.getInvokeId(), lastSeq, windowSize, msg.isMoreFollows()), false);

                // Append the window onto the original response.
                for (Segmentable segment : segmentWindow.getSegments()) {
                    if (segment != null)
                        ctx.getSegmentedMessage().appendServiceData(segment.getServiceData());
                }
                segmentWindow.clear(lastSeq + 1);
            }
        }

        if (msg.isMoreFollows()) {
            // Put the value back in the pending requests.
            ctx.reset(segTimeout * 4, 0);
            unackedMessages.add(key, ctx);
        }
        else if (msg instanceof ComplexACK)
            // We're done receiving the segmented response.
            completeComplexAckResponse((ComplexACK) ctx.getSegmentedMessage(), ctx.getConsumer());
        else
            // We're done receiving the segmented request.
            incomingConfirmedRequest((ConfirmedRequest) ctx.getSegmentedMessage(), key.getAddress(),
                    key.getLinkService(), msg.getInvokeId());
    }

    private void completeComplexAckResponse(ComplexACK cack, ResponseConsumer consumer) {
        try {
            cack.parseServiceData();
            consumer.success(cack.getService());
        }
        catch (BACnetException e) {
            consumer.ex(e);
        }
    }

    /**
     * The first part of the segmented message has already been sent. This is called each time a segment ack is
     * received.
     * 
     * This method handles outgoing segmented requests and responses.
     */
    private void segmentedOutgoing(UnackedMessageKey key, UnackedMessageContext ctx, SegmentACK ack) {
        // TODO handle NAK

        if (ctx.getServiceData().size() == 0) {
            // There any no more segments to send. If this is a request, expect the response.
            if (ctx.getOriginalApdu() instanceof ConfirmedRequest)
                unackedMessages.add(key, ctx);
            // However, if this is a response, there is nothing left to do.
            return;
        }

        // This may be a segment ack for an inter-window segment. We ignore all segment acks except for the
        // one for the last segment that was sent.
        if (ack.getSequenceNumber() < ctx.getLastIdSent())
            return;

        int remaining = ack.getActualWindowSize();

        // Send the next window of messages.
        int sequenceNumber = ctx.getLastIdSent();
        while (remaining > 0 && ctx.getServiceData().size() > 0) {
            ByteQueue segData = ctx.getNextSegment();
            APDU segment = ctx.getSegmentTemplate().clone(ctx.getServiceData().size() > 0, ++sequenceNumber,
                    ack.getActualWindowSize(), segData);

            try {
                network.sendAPDU(key.getAddress(), key.getLinkService(), segment, false);
            }
            catch (BACnetException e) {
                ctx.getConsumer().ex(e);
                return;
            }

            remaining--;
        }
        ctx.setLastIdSent(sequenceNumber);

        // Expect the segment ack.
        unackedMessages.add(key, ctx);
    }

    private void incomingConfirmedRequest(ConfirmedRequest confAPDU, Address address, OctetString linkService,
            byte invokeId) {
        try {
            try {
                confAPDU.parseServiceData();
                AcknowledgementService ackService = handleConfirmedRequest(address, invokeId,
                        confAPDU.getServiceRequest());
                sendConfirmedResponse(address, linkService, confAPDU, ackService);
            }
            catch (BACnetErrorException e) {
                network.sendAPDU(address, linkService, new com.serotonin.bacnet4j.apdu.Error(invokeId, e.getError()),
                        false);
            }
            catch (BACnetRejectException e) {
                network.sendAPDU(address, linkService, new Reject(invokeId, e.getRejectReason()), false);
            }
            catch (BACnetException e) {
                LOG.warn("Error handling incoming request", e);
                com.serotonin.bacnet4j.apdu.Error error = new com.serotonin.bacnet4j.apdu.Error(confAPDU.getInvokeId(),
                        new BaseError((byte) 127, new BACnetError(ErrorClass.services, ErrorCode.operationalProblem)));
                network.sendAPDU(address, linkService, error, false);
                localDevice.getExceptionDispatcher().fireReceivedException(e);
            }
        }
        catch (BACnetException e) {
            localDevice.getExceptionDispatcher().fireReceivedException(e);
        }
    }

    private AcknowledgementService handleConfirmedRequest(Address from, byte invokeId, ConfirmedRequestService service)
            throws BACnetException {
        try {
            return service.handle(localDevice, from);
        }
        catch (NotImplementedException e) {
            LOG.warn("Unsupported confirmed request: invokeId=" + invokeId + ", from=" + from + ", request="
                    + service.getClass().getName());
            throw new BACnetErrorException(ErrorClass.services, ErrorCode.serviceRequestDenied);
        }
        catch (BACnetErrorException e) {
            throw e;
        }
        catch (Exception e) {
            throw new BACnetErrorException(ErrorClass.device, ErrorCode.operationalProblem);
        }
    }

    private void sendConfirmedResponse(final Address address, final OctetString linkService,
            final ConfirmedRequest request, final AcknowledgementService response) throws BACnetException {
        if (response == null)
            network.sendAPDU(address, linkService,
                    new SimpleACK(request.getInvokeId(), request.getServiceRequest().getChoiceId()), false);
        else {
            // A complex ack response. Serialize the data.
            final ByteQueue serviceData = new ByteQueue();
            response.write(serviceData);

            // Check if we need to segment the message.
            if (serviceData.size() > request.getMaxApduLengthAccepted().getMaxLength()
                    - ComplexACK.getHeaderSize(false)) {
                final int maxServiceData = request.getMaxApduLengthAccepted().getMaxLength()
                        - ComplexACK.getHeaderSize(true);
                // Check if the device can accept what we want to send.
                if (!request.isSegmentedResponseAccepted())
                    throw new ServiceTooBigException("Response too big to send to device without segmentation");
                int segmentsRequired = serviceData.size() / maxServiceData + 1;
                if (segmentsRequired > request.getMaxSegmentsAccepted().getMaxSegments() || segmentsRequired > 128)
                    throw new ServiceTooBigException("Response too big to send to device; too many segments required");

                // Prepare the segmenting session.
                UnackedMessageContext ctx = new UnackedMessageContext(timeout, retries, null);
                UnackedMessageKey key = unackedMessages.addServer(address, linkService, request.getInvokeId(), ctx);

                ctx.setSegmentTemplate(
                        new ComplexACK(true, true, request.getInvokeId(), 0, segWindow, response.getChoiceId(), null));
                ctx.setServiceData(serviceData);
                ctx.setSegBuf(new byte[maxServiceData]);

                // Send an initial message to negotiate communication terms.
                APDU apdu = ctx.getSegmentTemplate().clone(true, 0, segWindow, ctx.getNextSegment());

                ctx.setOriginalApdu(apdu);
                sendForResponse(key, ctx);
            }
            else
                // We can send the whole APDU in one shot.
                network.sendAPDU(address, linkService,
                        new ComplexACK(false, false, request.getInvokeId(), 0, 0, response), false);
        }
    }

    private boolean expire() {
        boolean didSomething = false;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UnackedMessageKey, UnackedMessageContext>> umIter = unackedMessages.getRequests().entrySet()
                .iterator();

        // Check for expired unacked messages
        while (umIter.hasNext()) {
            Map.Entry<UnackedMessageKey, UnackedMessageContext> e = umIter.next();
            UnackedMessageKey key = e.getKey();
            UnackedMessageContext ctx = e.getValue();
            if (ctx.isExpired(now)) {
                if (ctx.hasMoreAttempts()) {
                    // Resend
                    ctx.retry(timeout);
                    sendForResponse(key, ctx);
                }
                else {
                    // Timeout
                    if (ctx.getSegmentWindow() == null) {
                        // Not a segmented message, at least as far as we know.
                        ctx.getConsumer().ex(new BACnetTimeoutException());
                        umIter.remove();
                    }
                    else {
                        // A segmented message.
                        if (ctx.getSegmentWindow().isEmpty() && ctx.getConsumer() != null) {
                            // No segments received. Return a timeout.
                            ctx.getConsumer()
                                    .ex(new BACnetTimeoutException(
                                            "Timeout while waiting for segment part: invokeId=" + key.getInvokeId()
                                                    + ", sequenceId=" + ctx.getSegmentWindow().getFirstSequenceId()));
                            umIter.remove();
                        }
                        else if (ctx.getSegmentWindow().isEmpty())
                            LOG.warn("No segments received for message " + ctx.getOriginalApdu());
                        else {
                            // Return a NAK with the last sequence id received in order and start over.
                            try {
                                network.sendAPDU(key.getAddress(), key.getLinkService(),
                                        new SegmentACK(true, key.isFromServer(), key.getInvokeId(),
                                                ctx.getSegmentWindow().getLatestSequenceId(),
                                                ctx.getSegmentWindow().getWindowSize(), true),
                                        false);
                            }
                            catch (BACnetException ex) {
                                ctx.getConsumer().ex(ex);
                                umIter.remove();
                            }
                        }
                    }
                }

                didSomething = true;
            }
        }

        return !didSomething;
    }

    void sendForResponse(UnackedMessageKey key, UnackedMessageContext ctx) {
        try {
            network.sendAPDU(key.getAddress(), key.getLinkService(), ctx.getOriginalApdu(), false);
        }
        catch (BACnetException e) {
            unackedMessages.remove(key);
            if (ctx.getConsumer() != null)
                ctx.getConsumer().ex(e);
            else
                LOG.error("", e);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((network == null) ? 0 : network.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DefaultTransport other = (DefaultTransport) obj;
        if (network == null) {
            if (other.network != null)
                return false;
        }
        else if (!network.equals(other.network))
            return false;
        return true;
    }
}
