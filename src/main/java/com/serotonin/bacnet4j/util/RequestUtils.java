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
package com.serotonin.bacnet4j.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.exception.AbortAPDUException;
import com.serotonin.bacnet4j.exception.BACnetErrorException;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetTimeoutException;
import com.serotonin.bacnet4j.exception.ErrorAPDUException;
import com.serotonin.bacnet4j.exception.ServiceTooBigException;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyMultipleAck;
import com.serotonin.bacnet4j.service.confirmed.AddListElementRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.service.confirmed.RemoveListElementRequest;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyRequest;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.ObjectPropertyReference;
import com.serotonin.bacnet4j.type.constructed.PropertyReference;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.ReadAccessResult;
import com.serotonin.bacnet4j.type.constructed.ReadAccessResult.Result;
import com.serotonin.bacnet4j.type.constructed.ReadAccessSpecification;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.WriteAccessSpecification;
import com.serotonin.bacnet4j.type.enumerated.AbortReason;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.error.ErrorClassAndCode;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.sero.Utils;

public class RequestUtils {
    static final Logger LOG = LoggerFactory.getLogger(RequestUtils.class);

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Read properties
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Does not work with aggregate PIDs like "all".
     */
    public static <T extends Encodable> T getProperty(final LocalDevice localDevice, final RemoteDevice d,
            final PropertyIdentifier pid) throws BACnetException {
        return getProperty(localDevice, d, d.getObjectIdentifier(), pid);
    }

    /**
     * Does not work with aggregate PIDs like "all".
     */
    @SuppressWarnings("unchecked")
    public static <T extends Encodable> T getProperty(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier oid, final PropertyIdentifier pid) throws BACnetException {
        final Map<PropertyIdentifier, Encodable> map = getProperties(localDevice, d, oid, null, pid);

        final Encodable value = map.get(pid);
        if (value instanceof ErrorClassAndCode) {
            final ErrorClassAndCode e = (ErrorClassAndCode) value;
            throw new BACnetErrorException(e.getErrorClass(), e.getErrorCode());
        }

        return (T) value;
    }

    public static <T extends Encodable> T getProperty(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier oid, final PropertyIdentifier pid, final int indexBase1) throws BACnetException {
        return getProperty(localDevice, d, oid, pid, new UnsignedInteger(indexBase1));
    }

    @SuppressWarnings("unchecked")
    public static <T extends Encodable> T getProperty(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier oid, final PropertyIdentifier pid, final UnsignedInteger pin)
            throws BACnetException {
        final List<ObjectPropertyReference> refs = new ArrayList<>();
        refs.add(new ObjectPropertyReference(oid, pid, pin));
        final Map<PropertyIdentifier, Encodable> map = getProperties(localDevice, d, null, refs);
        return (T) map.get(pid);
    }

    public static Map<PropertyIdentifier, Encodable> getProperties(final LocalDevice localDevice, final RemoteDevice d,
            final ReadListener callback, final PropertyIdentifier... pids) throws BACnetException {
        return getProperties(localDevice, d, d.getObjectIdentifier(), callback, pids);
    }

    public static Map<PropertyIdentifier, Encodable> getProperties(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier obj, final ReadListener callback, final PropertyIdentifier... pids)
            throws BACnetException {
        final List<ObjectPropertyReference> refs = new ArrayList<>(pids.length);
        for (int i = 0; i < pids.length; i++)
            refs.add(new ObjectPropertyReference(obj, pids[i]));
        return getProperties(localDevice, d, callback, refs);
    }

    private static Map<PropertyIdentifier, Encodable> getProperties(final LocalDevice localDevice, final RemoteDevice d,
            final ReadListener callback, final List<ObjectPropertyReference> refs) throws BACnetException {
        final List<Pair<ObjectPropertyReference, Encodable>> values = readProperties(localDevice, d, refs, callback);

        final Map<PropertyIdentifier, Encodable> map = new HashMap<>(values.size());
        for (final Pair<ObjectPropertyReference, Encodable> pair : values)
            map.put(pair.getLeft().getPropertyIdentifier(), pair.getRight());
        return map;
    }

    public static Encodable sendReadPropertyAllowNull(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier oid, final PropertyIdentifier pid) throws BACnetException {
        return sendReadPropertyAllowNull(localDevice, d, oid, pid, null, null);
    }

    public static SequenceOf<ObjectIdentifier> getObjectList(final LocalDevice localDevice, final RemoteDevice d)
            throws BACnetException {
        return getObjectList(localDevice, d, null);
    }

    @SuppressWarnings("unchecked")
    public static SequenceOf<ObjectIdentifier> getObjectList(final LocalDevice localDevice, final RemoteDevice d,
            final ReadListener callback) throws BACnetException {
        return (SequenceOf<ObjectIdentifier>) sendReadPropertyAllowNull(localDevice, d, d.getObjectIdentifier(),
                PropertyIdentifier.objectList, null, callback);
    }

    /**
     * Sends a ReadProperty-Request and ignores Error responses where the class is Property and the code is
     * unknownProperty. Returns null in this case.
     */
    public static Encodable sendReadPropertyAllowNull(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier oid, final PropertyIdentifier pid, final UnsignedInteger propertyArrayIndex,
            final ReadListener callback) throws BACnetException {
        try {
            final ReadPropertyAck ack = (ReadPropertyAck) localDevice
                    .send(d, new ReadPropertyRequest(oid, pid, propertyArrayIndex)).get();
            if (callback != null)
                callback.progress(1, d.getInstanceNumber(), oid, pid, propertyArrayIndex, ack.getValue());
            return ack.getValue();
        } catch (final AbortAPDUException e) {
            if (e.getApdu().getAbortReason() == AbortReason.bufferOverflow.intValue()
                    || e.getApdu().getAbortReason() == AbortReason.segmentationNotSupported.intValue()) {
                // The response may be too long to send. If the property is a sequence...
                if (ObjectProperties.getObjectPropertyTypeDefinition(oid.getObjectType(), pid)
                        .getPropertyTypeDefinition().isCollection()) {
                    LOG.info("Received abort exception on sequence request. Sending chunked reference request instead");

                    // ... then try getting it by sending requests for indices. Find out how many there are.
                    final int len = ((UnsignedInteger) sendReadPropertyAllowNull(localDevice, d, oid, pid,
                            UnsignedInteger.ZERO, null)).intValue();

                    // Create a list of individual property references.
                    final PropertyReferences refs = new PropertyReferences();
                    for (int i = 1; i <= len; i++)
                        refs.add(oid, new PropertyReference(pid, new UnsignedInteger(i)));

                    // Send the request. Use the method that automatically partitions the request.
                    final PropertyValues pvs = readProperties(localDevice, d, refs, callback);

                    // We know that the original request property was a sequence, so create one to store the result.
                    final SequenceOf<Encodable> list = new SequenceOf<>();
                    for (int i = 1; i <= len; i++)
                        list.add(pvs.getNoErrorCheck(oid, new PropertyReference(pid, new UnsignedInteger(i))));

                    // And there you go.
                    return list;
                }
                throw e;
            }
            throw e;
        } catch (final ErrorAPDUException e) {
            if (e.getError().equals(ErrorClass.property, ErrorCode.unknownProperty))
                return null;
            throw e;
        }
    }

    public static Encodable readProperty(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier oid, final PropertyIdentifier pid, final UnsignedInteger propertyArrayIndex)
            throws BACnetException {
        if (d.getServicesSupported().isReadProperty()) {
            final ReadPropertyAck ack = (ReadPropertyAck) localDevice
                    .send(d, new ReadPropertyRequest(oid, pid, propertyArrayIndex)).get();
            return ack.getValue();
        }

        if (d.getServicesSupported().isReadPropertyMultiple()) {
            final List<PropertyReference> refs = new ArrayList<>();
            refs.add(new PropertyReference(pid, propertyArrayIndex));
            final List<ReadAccessSpecification> specs = new ArrayList<>();
            specs.add(new ReadAccessSpecification(oid, new SequenceOf<>(refs)));
            final ReadPropertyMultipleAck ack = (ReadPropertyMultipleAck) localDevice
                    .send(d, new ReadPropertyMultipleRequest(new SequenceOf<>(specs))).get();
            return ack.getListOfReadAccessResults().getBase1(1).getListOfResults().getBase1(1).getReadResult()
                    .getDatum();
        }

        throw new BACnetException("Device does not support readProperty nor readPropertyMultiple");
    }

    /**
     * This version of the readProperties method will preserve the order of properties given in the list in the results.
     *
     * @param d
     *            the device to which to send the request
     * @param oprs
     *            the list of property references to request
     * @return a list of the original property reference objects wrapped with their values
     * @throws BACnetException
     */
    public static List<Pair<ObjectPropertyReference, Encodable>> readProperties(final LocalDevice localDevice,
            final RemoteDevice d, final List<ObjectPropertyReference> oprs, final ReadListener callback)
            throws BACnetException {
        final PropertyReferences refs = new PropertyReferences();
        for (final ObjectPropertyReference opr : oprs)
            refs.addIndex(opr.getObjectIdentifier(), opr.getPropertyIdentifier(), opr.getPropertyArrayIndex());

        final PropertyValues pvs = readProperties(localDevice, d, refs, callback);

        // Read the properties in the same order.
        final List<Pair<ObjectPropertyReference, Encodable>> results = new ArrayList<>();
        for (final ObjectPropertyReference opr : oprs)
            results.add(new ImmutablePair<>(opr, pvs.getNoErrorCheck(opr)));

        return results;
    }

    public static PropertyValues readProperties(final LocalDevice localDevice, final RemoteDevice d,
            final PropertyReferences refs, final ReadListener callback) throws BACnetException {
        Map<ObjectIdentifier, List<PropertyReference>> properties;
        final PropertyValues propertyValues = new PropertyValues();
        final ReadListenerUpdater updater = new ReadListenerUpdater(callback, propertyValues, refs.size());

        final boolean multipleSupported = d.getServicesSupported() != null
                && d.getServicesSupported().isReadPropertyMultiple();

        boolean forceMultiple = false;
        // Check if a "special" property identifier is contained in the references.
        for (final List<PropertyReference> prs : refs.getProperties().values()) {
            for (final PropertyReference pr : prs) {
                final PropertyIdentifier pi = pr.getPropertyIdentifier();
                if (pi.equals(PropertyIdentifier.all) || pi.equals(PropertyIdentifier.required)
                        || pi.equals(PropertyIdentifier.optional)) {
                    forceMultiple = true;
                    break;
                }
            }

            if (forceMultiple)
                break;
        }

        if (forceMultiple && !multipleSupported)
            throw new BACnetException("Cannot send request. ReadPropertyMultiple is required but not supported.");

        if (forceMultiple || refs.size() > 1 && multipleSupported) {
            // Read property multiple can be used. Determine the max references
            final int maxRef = d.getMaxReadMultipleReferences();

            // If the device supports read property multiple, send them all at once, or at least in partitions.
            List<PropertyReferences> partitions = refs.getPropertiesPartitioned(maxRef);
            int counter = 0;
            while (!partitions.isEmpty()) {
                final PropertyReferences partition = partitions.get(0);
                properties = partition.getProperties();
                final List<ReadAccessSpecification> specs = new ArrayList<>();
                for (final ObjectIdentifier oid : properties.keySet())
                    specs.add(new ReadAccessSpecification(oid, new SequenceOf<>(properties.get(oid))));

                final ReadPropertyMultipleRequest request = new ReadPropertyMultipleRequest(new SequenceOf<>(specs));

                ReadPropertyMultipleAck ack;
                try {
                    ack = (ReadPropertyMultipleAck) localDevice.send(d, request).get();
                    counter++;

                    final List<ReadAccessResult> results = ack.getListOfReadAccessResults().getValues();
                    ObjectIdentifier oid;
                    for (final ReadAccessResult objectResult : results) {
                        oid = objectResult.getObjectIdentifier();
                        for (final Result result : objectResult.getListOfResults().getValues()) {
                            updater.increment(d.getInstanceNumber(), oid, result.getPropertyIdentifier(),
                                    result.getPropertyArrayIndex(), result.getReadResult().getDatum());
                            if (updater.cancelled())
                                break;
                        }

                        if (updater.cancelled())
                            break;
                    }

                    partitions.remove(0);
                } catch (final ServiceTooBigException e) {
                    if (partition.size() < 2)
                        throw e;

                    // Reduce the device's max references.
                    d.reduceMaxReadMultipleReferences(partition.size());

                    // Create a new PropertyReferences instance from the remaining references.
                    final PropertyReferences remaining = new PropertyReferences(partitions);

                    // Repartition the remaining requests.
                    partitions = remaining.getPropertiesPartitioned(d.getMaxReadMultipleReferences());
                } catch (final AbortAPDUException e) {
                    LOG.warn("Chunked request failed.");
                    if (AbortReason.bufferOverflow.equals(e.getApdu().getAbortReason())
                            || AbortReason.segmentationNotSupported.equals(e.getApdu().getAbortReason())) {
                        if (partition.size() < 2)
                            throw e;

                        // Reduce the device's max references.
                        d.reduceMaxReadMultipleReferences(partition.size());

                        // Create a new PropertyReferences instance from the remaining references.
                        final PropertyReferences remaining = new PropertyReferences(partitions);

                        // Repartition the remaining requests.
                        partitions = remaining.getPropertiesPartitioned(d.getMaxReadMultipleReferences());
                    } else
                        throw new BACnetException("Completed " + counter + " requests. Excepted on: " + request, e);
                } catch (final BACnetTimeoutException e) {
                    if (counter == 0) {
                        // 如果counter == 0就出现异常说明是设备通讯有问题
                        throw e;
                    }

					// 如果 counter > 0 说明这个remoteDevice的读取多个属性请求成功过至少一次，
					// 这时再出现异常说明是remoteDevice的某些对象出问题了。
                    populateWithError(d, properties, updater,
                            new ErrorClassAndCode(ErrorClass.device, ErrorCode.timeout));
                    partitions.remove(0);
                } catch (final ErrorAPDUException e) {
					// 出现这个异常可能是因为remoteDevice只支持单个属性请求（官方文档说的）
					// PropertyReferences partition;
					// size()表示partition里的所有对象的所有属性的总数
					// partition.size() < 2 就没必要再继续请求了
					// 否则使用sendOneAtATime读取单个属性请求去一个个的读取对象的每一个属性（性能极差）
                    if (partition.size() < 2)
                        populateWithError(d, properties, updater, e.getError());
                    else {
                        sendOneAtATime(localDevice, d, partition, updater);
                    }
                    partitions.remove(0);
                } catch (final BACnetException e) {
                    throw new BACnetException("Completed " + counter + " requests. Excepted on: " + request, e);
                }

                if (updater.cancelled())
                    break;
            }
        } else {
            // 由于不支持读取多个属性请求，所以用读取单个属性请求一个个的读
            sendOneAtATime(localDevice, d, refs, updater);
        }

        return propertyValues;
    }
 
    public static void populateWithError(final RemoteDevice d,
            final Map<ObjectIdentifier, List<PropertyReference>> properties, final ReadListenerUpdater updater,
            final ErrorClassAndCode error) {
        for (final ObjectIdentifier oid : properties.keySet()) {
            for (final PropertyReference ref : properties.get(oid))
                updater.increment(d.getInstanceNumber(), oid, ref.getPropertyIdentifier(), ref.getPropertyArrayIndex(),
                        error);
        }
    }

    private static void sendOneAtATime(final LocalDevice localDevice, final RemoteDevice d,
            final PropertyReferences refs, final ReadListenerUpdater updater) throws BACnetException {
        LOG.debug("Making property reference requests one at a time");
        List<PropertyReference> refList;
        ReadPropertyRequest request;
        ReadPropertyAck ack;
        final Map<ObjectIdentifier, List<PropertyReference>> properties = refs.getProperties();
        boolean first = true;
        for (final ObjectIdentifier oid : properties.keySet()) {
            refList = properties.get(oid);
            for (final PropertyReference ref : refList) {
                request = new ReadPropertyRequest(oid, ref.getPropertyIdentifier(), ref.getPropertyArrayIndex());
                try {
                    ack = (ReadPropertyAck) localDevice.send(d, request).get();
                    updater.increment(d.getInstanceNumber(), oid, ack.getPropertyIdentifier(),
                            ack.getPropertyArrayIndex(), ack.getValue());
                } catch (final BACnetTimeoutException e) {
                    if (first) {
                        // If the first request, rethrow
                        throw e;
                    }
                    updater.increment(d.getInstanceNumber(), oid, ref.getPropertyIdentifier(),
                            ref.getPropertyArrayIndex(), new ErrorClassAndCode(ErrorClass.device, ErrorCode.timeout));
                } catch (final ErrorAPDUException e) {
                    updater.increment(d.getInstanceNumber(), oid, ref.getPropertyIdentifier(),
                            ref.getPropertyArrayIndex(), e.getError());
                }

                first = false;
                if (updater.cancelled())
                    break;
            }

            if (updater.cancelled())
                break;
        }
    }

    //    public static PropertyValues readPresentValues(final LocalDevice localDevice, final RemoteDevice d,
    //            final RequestListener callback) throws BACnetException {
    //        return readPresentValues(localDevice, d, d.getObjects(), callback);
    //    }
    //
    //    public static PropertyValues readPresentValues(final LocalDevice localDevice, final RemoteDevice d,
    //            final List<RemoteObject> objs, final RequestListener callback) throws BACnetException {
    //        final List<ObjectIdentifier> oids = new ArrayList<>(objs.size());
    //        for (final RemoteObject o : d.getObjects())
    //            oids.add(o.getObjectIdentifier());
    //        return readOidPresentValues(localDevice, d, oids, callback);
    //    }

    public static PropertyValues readOidPresentValues(final LocalDevice localDevice, final RemoteDevice d,
            final List<ObjectIdentifier> oids, final ReadListener callback) throws BACnetException {
        if (oids.size() == 0)
            return new PropertyValues();

        final PropertyReferences refs = new PropertyReferences();
        for (final ObjectIdentifier oid : oids)
            refs.add(oid, PropertyIdentifier.presentValue);

        return readProperties(localDevice, d, refs, callback);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Write properties
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void writePresentValue(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier oid, final Encodable value) throws BACnetException {
        writeProperty(localDevice, d, oid, PropertyIdentifier.presentValue, value);
    }

    public static void writeProperty(final LocalDevice localDevice, final RemoteDevice d, final ObjectIdentifier oid,
            final PropertyIdentifier pid, final Encodable value) throws BACnetException {
        localDevice.send(d, new WritePropertyRequest(oid, pid, null, value, null)).get();
    }

    public static void writeProperty(final LocalDevice localDevice, final RemoteDevice d, final ObjectIdentifier oid,
            final PropertyIdentifier pid, final Encodable value, final int priority) throws BACnetException {
        localDevice.send(d, new WritePropertyRequest(oid, pid, null, value, new UnsignedInteger(priority))).get();
    }

    public static void writeProperty(final LocalDevice localDevice, final RemoteDevice d, final ObjectIdentifier oid,
            final PropertyValue pv) throws BACnetException {
        writeProperty(localDevice, d, oid, pv.getPropertyIdentifier(), pv.getPropertyArrayIndex(), pv.getValue(),
                pv.getPriority());
    }

    public static void writeProperty(final LocalDevice localDevice, final RemoteDevice d, final ObjectIdentifier oid,
            final PropertyIdentifier pid, final int propertyArrayIndex, final Encodable value) throws BACnetException {
        writeProperty(localDevice, d, oid, pid, new UnsignedInteger(propertyArrayIndex), value, null);
    }

    public static void writeProperty(final LocalDevice localDevice, final RemoteDevice d, final ObjectIdentifier oid,
            final PropertyIdentifier pid, final int propertyArrayIndex, final Encodable value, final int priority)
            throws BACnetException {
        writeProperty(localDevice, d, oid, pid, new UnsignedInteger(propertyArrayIndex), value,
                new UnsignedInteger(priority));
    }

    public static void writeProperty(final LocalDevice localDevice, final RemoteDevice d, final ObjectIdentifier oid,
            final PropertyIdentifier pid, final UnsignedInteger propertyArrayIndex, final Encodable value,
            final UnsignedInteger priority) throws BACnetException {
        writeProperties(localDevice, d, Utils.toList(new WriteAccessSpecification(oid,
                new SequenceOf<>(new PropertyValue(pid, propertyArrayIndex, value, priority)))));
    }

    public static void writeProperties(final LocalDevice localDevice, final RemoteDevice d, final ObjectIdentifier oid,
            final List<PropertyValue> props) throws BACnetException {
        writeProperties(localDevice, d, Utils.toList(new WriteAccessSpecification(oid, new SequenceOf<>(props))));
    }

    public static void writeProperties(final LocalDevice localDevice, final RemoteDevice d,
            final List<WriteAccessSpecification> specs) throws BACnetException {
        int sum = 0;
        for (final WriteAccessSpecification spec : specs)
            sum += spec.size();

        if (sum == 0)
            return;
        if (!d.getServicesSupported().isWriteProperty() && !d.getServicesSupported().isWritePropertyMultiple())
            throw new BACnetException("Unable to write. Device " + d.getInstanceNumber()
                    + " does not support writeProperty nor writePropertyMultiple");

        boolean multiple = false;
        if (sum > 1 && d.getServicesSupported().isWritePropertyMultiple())
            multiple = true;
        else if (sum == 1 && !d.getServicesSupported().isWriteProperty())
            // Only one property to write, but a single write is not supported.
            multiple = true;

        if (multiple) {
            // TODO We could be sending more specs than the device will accept at once. If an error is returned
            // indicating such, and write single is supported, we can try writing one at a time.
            localDevice.send(d, new WritePropertyMultipleRequest(new SequenceOf<>(specs))).get();
        } else {
            for (final WriteAccessSpecification spec : specs) {
                for (final PropertyValue pv : spec.getListOfProperties()) {
                    localDevice.send(d, new WritePropertyRequest(spec.getObjectIdentifier(), pv.getPropertyIdentifier(),
                            pv.getPropertyArrayIndex(), pv.getValue(), pv.getPriority())).get();
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // List element write requests
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void addListElement(final LocalDevice localDevice, final RemoteDevice d, final ObjectIdentifier oid,
            final PropertyIdentifier pid, final Encodable value) throws BACnetException {
        if (d.getServicesSupported().isAddListElement()) {
            final SequenceOf<Encodable> values = new SequenceOf<>();
            values.add(value);
            localDevice.send(d, new AddListElementRequest(oid, pid, null, values)).get();
        } else {
            @SuppressWarnings("unchecked")
            final SequenceOf<Encodable> list = (SequenceOf<Encodable>) readProperty(localDevice, d, oid, pid, null);
            if (!list.contains(value)) {
                list.add(value);
                writeProperty(localDevice, d, oid, pid, list);
            }
        }
    }

    public static void removeListElement(final LocalDevice localDevice, final RemoteDevice d,
            final ObjectIdentifier oid, final PropertyIdentifier pid, final Encodable value) throws BACnetException {
        if (d.getServicesSupported().isRemoveListElement()) {
            final SequenceOf<Encodable> values = new SequenceOf<>();
            values.add(value);
            localDevice.send(d, new RemoveListElementRequest(oid, pid, null, values)).get();
        } else {
            @SuppressWarnings("unchecked")
            final SequenceOf<Encodable> list = (SequenceOf<Encodable>) readProperty(localDevice, d, oid, pid, null);
            if (list.contains(value)) {
                list.remove(value);
                writeProperty(localDevice, d, oid, pid, list);
            }
        }
    }
}
