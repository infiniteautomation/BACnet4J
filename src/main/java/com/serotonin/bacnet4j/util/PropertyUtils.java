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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetRuntimeException;
import com.serotonin.bacnet4j.exception.BACnetTimeoutException;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.PropertyReference;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.error.ErrorClassAndCode;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public class PropertyUtils {
    static final Logger LOG = LoggerFactory.getLogger(PropertyUtils.class);

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Reading properties
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static DeviceObjectPropertyValues readProperties(final LocalDevice localDevice,
            final DeviceObjectPropertyReferences refs, final ReadListener callback) {
        return readProperties(localDevice, refs, callback, 0);
    }

    /**
     * A blocking call to retrieve properties from potentially multiple devices, using the property cache where
     * possible to improve performance. Note that this call can modify the given DeviceObjectPropertyReferences
     * object.
     *
     * @param localDevice
     *            the local device
     * @param refs
     *            the references to retrieve. This object may be modified during this call.
     * @param callback
     *            the progress monitor. Optional.
     * @param deviceTimeout
     *            the timeout for the lookup of devices that are not currently known. A value <= 0 means to use
     *            the default timeout.
     * @return
     */
    public static DeviceObjectPropertyValues readProperties(final LocalDevice localDevice,
            final DeviceObjectPropertyReferences refs, final ReadListener callback, final long deviceTimeout) {
        final long timeoutToUse;
        if (deviceTimeout <= 0) {
            timeoutToUse = localDevice.getTransportTimeout();
            LOG.debug("Using local device timeout for reading properties: {}", timeoutToUse);
        } else {
            timeoutToUse = deviceTimeout;
            LOG.debug("Using provided parameter as timeout for reading properties: {}", timeoutToUse);
        }

        final DeviceObjectPropertyValues result = new DeviceObjectPropertyValues();

        final Map<Integer, PropertyReferences> properties = refs.getProperties();

        final AtomicInteger completedProperties = new AtomicInteger();
        final double totalProperties = refs.size();

        // TODO should get the device first and then look for cached properties, since the retrieval of the device
        // will fill in some of its properties.

        // Find the properties that we already have. Use iterators so that we can remove as we go the properties that
        // are found in the cache.
        final Iterator<Map.Entry<Integer, PropertyReferences>> deviceIter = properties.entrySet().iterator();
        while (deviceIter.hasNext()) {
            final Map.Entry<Integer, PropertyReferences> device = deviceIter.next();
            final Integer did = device.getKey();

            final Iterator<Map.Entry<ObjectIdentifier, List<PropertyReference>>> objectIter = device.getValue()
                    .getProperties().entrySet().iterator();
            while (objectIter.hasNext()) {
                final Map.Entry<ObjectIdentifier, List<PropertyReference>> object = objectIter.next();
                final ObjectIdentifier oid = object.getKey();

                final Iterator<PropertyReference> propertyIter = object.getValue().iterator();
                while (propertyIter.hasNext()) {
                    final PropertyReference property = propertyIter.next();
                    final PropertyIdentifier pid = property.getPropertyIdentifier();
                    final UnsignedInteger pin = property.getPropertyArrayIndex();

                    try {
                        final Encodable value = localDevice.getCachedRemoteProperty(did, oid, pid, pin);
                        if (value != null) {
                            // Found the property. Remove it from the request list.
                            propertyIter.remove();
                            // Update the result and callback.
                            updateResultAndCallback(result, callback, did, oid, pid, pin, value, completedProperties,
                                    totalProperties);
                        }
                    } catch (@SuppressWarnings("unused") final BACnetRuntimeException e) {
                        // Ignore this. It means an array index was specified for a property that is not an
                        // array/sequence. The device itself should say as much when it responds.
                    }
                }

                if (object.getValue().isEmpty()) {
                    // All of the properties for this object were found in the cache, so remove the object from
                    // the property references list.
                    objectIter.remove();
                }
            }

            if (device.getValue().size() == 0) {
                // All of the properties for this device were found in the cache, so remove the device from
                // the property references list.
                deviceIter.remove();
            }
        }

        // Any property references that remain need to be requested from the devices.
        final List<Future<?>> futures = new ArrayList<>();

        for (final Map.Entry<Integer, PropertyReferences> dev : properties.entrySet()) {
            final Integer deviceId = dev.getKey();
            final PropertyReferences propRefs = dev.getValue();

            // Check if the remote device is already cached.
            // TODO: don't need to check for a cached rd here because the ld will do that anyway.
            final RemoteDevice rd = localDevice.getCachedRemoteDevice(deviceId);
            Runnable runnable;
            if (rd == null) {
                // Initiate a device lookup
                runnable = () -> {
                    requestPropertiesFromDevice(localDevice, deviceId, timeoutToUse, propRefs, callback, result,
                            completedProperties, totalProperties);
                };
            } else {
                runnable = () -> {
                    // Try to get the properties from the cached device.
                    try {
                        requestRemoteDeviceProperties(localDevice, rd, propRefs, callback, completedProperties,
                                totalProperties, result);
                    } catch (@SuppressWarnings("unused") final BACnetTimeoutException e) {
                        // The cached device appears to be offline. Remove it from the cache, and try discovering it
                        // again in case its address changed.
                        localDevice.removeCachedRemoteDevice(deviceId);
                        requestPropertiesFromDevice(localDevice, deviceId, timeoutToUse, propRefs, callback, result,
                                completedProperties, totalProperties);
                    }
                };
            }

            futures.add(localDevice.submit(runnable));
        }

        // Wait on the futures
        for (final Future<?> future : futures) {
            try {
                future.get();
            } catch (final Exception e) {
                LOG.error("Error in future", e);
            }
        }

        return result;
    }

    private static void requestPropertiesFromDevice(final LocalDevice localDevice, final int deviceId,
            final long deviceTimeout, final PropertyReferences propRefs, final ReadListener callback,
            final DeviceObjectPropertyValues result, final AtomicInteger completedProperties,
            final double totalProperties) {
        try {
            final RemoteDevice r = localDevice.getRemoteDevice(deviceId).get(deviceTimeout);
            requestRemoteDeviceProperties(localDevice, r, propRefs, callback, completedProperties, totalProperties,
                    result);
        } catch (final BACnetTimeoutException e) {
            LOG.error("Timeout while finding device {}", deviceId, e);

            // Set all of the properties for the request to an error.
            final ErrorClassAndCode error = new ErrorClassAndCode(ErrorClass.device, ErrorCode.timeout);
            for (final Map.Entry<ObjectIdentifier, List<PropertyReference>> obj : propRefs.getProperties().entrySet()) {
                for (final PropertyReference ref : obj.getValue()) {
                    updateResultAndCallback(result, callback, deviceId, obj.getKey(), ref.getPropertyIdentifier(),
                            ref.getPropertyArrayIndex(), error, completedProperties, totalProperties);
                }
            }
        } catch (final BACnetException e) {
            completedProperties.addAndGet(propRefs.size());
            LOG.error("Exception while finding device {}", deviceId, e);
        }
    }

    private static void updateResultAndCallback(final DeviceObjectPropertyValues result, final ReadListener callback,
            final int did, final ObjectIdentifier oid, final PropertyIdentifier pid, final UnsignedInteger pin,
            final Encodable value, final AtomicInteger completedProperties, final double totalProperties) {
        // Add it to the result list.
        synchronized (result) {
            result.add(did, oid, pid, pin, value);
        }

        if (callback != null) {
            // Notify the callback
            final double progress = completedProperties.incrementAndGet() / totalProperties;
            callback.progress(progress, did, oid, pid, pin, value);
        }
    }

    private static void requestRemoteDeviceProperties(final LocalDevice localDevice, final RemoteDevice rd,
            final PropertyReferences refs, final ReadListener callback, final AtomicInteger completedProperties,
            final double totalProperties, final DeviceObjectPropertyValues result) throws BACnetTimeoutException {
        LOG.info("Properties to read from {}: {}", rd.getInstanceNumber(), refs.size());

        final AtomicInteger remaining = new AtomicInteger(refs.size());
        try {
            final ReadListener deviceCallback = new ReadListener() {
                @Override
                public boolean progress(final double deviceProgress, final int did, final ObjectIdentifier oid,
                        final PropertyIdentifier pid, final UnsignedInteger pin, final Encodable value) {
                    // Notify the callback
                    remaining.decrementAndGet();

                    // Add to the result list.
                    synchronized (result) {
                        result.add(did, oid, pid, pin, value);
                    }

                    // Cache the retrieve objects and properties.
                    rd.setObjectProperty(oid, pid, pin, value);

                    final double progress = completedProperties.incrementAndGet() / totalProperties;
                    if (callback == null)
                        return false;
                    return callback.progress(progress, did, oid, pid, pin, value);
                }
            };

            // Request the rest of the properties.
            RequestUtils.readProperties(localDevice, rd, refs, deviceCallback);
        } catch (final BACnetTimeoutException ex) {
            throw ex;
        } catch (final BACnetException ex) {
            completedProperties.addAndGet(remaining.get());
            LOG.error("Exception while getting properties for device {}", rd.getInstanceNumber(), ex);
        }
    }

    //
    //    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //    // Writing properties
    //    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //    public static void writeProperties(final LocalDevice localDevice,
    //            final DeviceObjectPropertyReferenceValues values) {
    //        return writeProperties(localDevice, values, 0);
    //    }
    //
    //    public static Map<Integer, BACnetException> writeProperties(final LocalDevice localDevice,
    //            final DeviceObjectPropertyReferenceValues values, final long deviceTimeout) {
    //        final Map<Integer, BACnetException> result = new HashMap<>();
    //
    //        final List<Future<?>> futures = new ArrayList<>();
    //
    //        for (final Map.Entry<Integer, PropertyReferenceValues> dev : values.getProperties().entrySet()) {
    //            final Integer deviceId = dev.getKey();
    //            final PropertyReferenceValues propRefs = dev.getValue();
    //
    //            // Check if the remote device is already cached.
    //            final RemoteDevice rd = localDevice.getCachedRemoteDevice(deviceId);
    //            final Runnable runnable;
    //            if (rd == null) {
    //                // Initiate a device lookup
    //                runnable = () -> {
    //                    writePropertiesToDevice(localDevice, deviceId, deviceTimeout, propRefs, callback, result,
    //                            completedProperties, totalProperties);
    //                };
    //            } else {
    //                //                runnable = () -> {
    //                //                    // Try to get the properties from the cached device.
    //                //                    try {
    //                //                        requestRemoteDeviceProperties(localDevice, rd, propRefs, callback, completedProperties,
    //                //                                totalProperties, result);
    //                //                    } catch (@SuppressWarnings("unused") final BACnetTimeoutException e) {
    //                //                        // The cached device appears to be offline. Remove it from the cache, and try discovering it
    //                //                        // again in case its address changed.
    //                //                        localDevice.removeCachedRemoteDevice(deviceId);
    //                //                        requestPropertiesFromDevice(localDevice, deviceId, deviceTimeout, propRefs, callback, result,
    //                //                                completedProperties, totalProperties);
    //                //                    }
    //                //                };
    //            }
    //
    //            //            futures.add(localDevice.submit(runnable));
    //        }
    //
    //        //        // Wait on the futures
    //        //        for (final Future<?> future : futures) {
    //        //            try {
    //        //                future.get();
    //        //            } catch (final Exception e) {
    //        //                LOG.error("Error in future", e);
    //        //            }
    //        //        }
    //
    //        return result;
    //    }
    //
    //    private static void writePropertiesToDevice(final LocalDevice localDevice, final int deviceId,
    //            final long deviceTimeout, final PropertyReferenceValues values,
    //            final Map<Integer, BACnetException> result) {
    //        try {
    //            final RemoteDevice r = localDevice.getRemoteDevice(deviceId).get(deviceTimeout);
    //            writeRemoteDeviceProperties(localDevice, r, values, result);
    //        } catch (final BACnetException e) {
    //            if (e instanceof BACnetTimeoutException) {
    //                // Clear the remote device from the cache
    //                localDevice.removeCachedRemoteDevice(deviceId);
    //            }
    //
    //            synchronized (result) {
    //                result.put(deviceId, e);
    //            }
    //            LOG.error("Exception while finding device {}", deviceId, e);
    //        }
    //    }
    //
    //    private static void writeRemoteDeviceProperties(final LocalDevice localDevice, final RemoteDevice rd,
    //            final PropertyReferenceValues values, final Map<Integer, BACnetException> result) throws BACnetTimeoutException {
    //        LOG.info("Properties to write to {}: {}", rd.getInstanceNumber(), values.size());
    //
    //        try {
    //            WriteAccessSpecification
    //
    //            RequestUtils.writeProperties(localDevice, rd, values., deviceCallback);
    //        } catch (final BACnetTimeoutException ex) {
    ////            throw ex;
    //        } catch (final BACnetException ex) {
    ////            completedProperties.addAndGet(remaining.get());
    ////            LOG.error("Exception while getting properties for device {}", rd.getInstanceNumber(), ex);
    //        }
    //    }
}
