/*
 * ============================================================================
 * GNU Lesser General Public License
 * ============================================================================
 *
 * Copyright (C) 2006-2009 Serotonin Software Technologies Inc. http://serotoninsoftware.com
 * @author Matthew Lohbihler
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307, USA.
 */
package com.serotonin.bacnet4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.serotonin.bacnet4j.apdu.APDU;
import com.serotonin.bacnet4j.apdu.ComplexACK;
import com.serotonin.bacnet4j.apdu.ConfirmedRequest;
import com.serotonin.bacnet4j.apdu.Segmentable;
import com.serotonin.bacnet4j.apdu.SimpleACK;
import com.serotonin.bacnet4j.apdu.UnconfirmedRequest;
import com.serotonin.bacnet4j.enums.DayOfWeek;
import com.serotonin.bacnet4j.enums.MaxApduLength;
import com.serotonin.bacnet4j.enums.MaxSegments;
import com.serotonin.bacnet4j.enums.Month;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.service.acknowledgement.AcknowledgementService;
import com.serotonin.bacnet4j.service.acknowledgement.AtomicReadFileAck;
import com.serotonin.bacnet4j.service.acknowledgement.AtomicReadFileAck.RecordAccessAck;
import com.serotonin.bacnet4j.service.acknowledgement.AtomicReadFileAck.StreamAccessAck;
import com.serotonin.bacnet4j.service.acknowledgement.AtomicWriteFileAck;
import com.serotonin.bacnet4j.service.acknowledgement.ConfirmedPrivateTransferAck;
import com.serotonin.bacnet4j.service.acknowledgement.CreateObjectAck;
import com.serotonin.bacnet4j.service.acknowledgement.GetAlarmSummaryAck;
import com.serotonin.bacnet4j.service.acknowledgement.GetEnrollmentSummaryAck;
import com.serotonin.bacnet4j.service.acknowledgement.GetEventInformationAck;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyMultipleAck;
import com.serotonin.bacnet4j.service.acknowledgement.ReadRangeAck;
import com.serotonin.bacnet4j.service.acknowledgement.VtDataAck;
import com.serotonin.bacnet4j.service.acknowledgement.VtOpenAck;
import com.serotonin.bacnet4j.service.confirmed.AcknowledgeAlarmRequest;
import com.serotonin.bacnet4j.service.confirmed.AddListElementRequest;
import com.serotonin.bacnet4j.service.confirmed.AtomicReadFileRequest;
import com.serotonin.bacnet4j.service.confirmed.AtomicReadFileRequest.RecordAccess;
import com.serotonin.bacnet4j.service.confirmed.AtomicReadFileRequest.StreamAccess;
import com.serotonin.bacnet4j.service.confirmed.AtomicWriteFileRequest;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedCovNotificationMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedCovNotificationMultipleRequest.CovNotification;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedCovNotificationMultipleRequest.CovNotification.CovNotificationValue;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedCovNotificationRequest;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedEventNotificationRequest;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedPrivateTransferRequest;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedRequestService;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedTextMessageRequest;
import com.serotonin.bacnet4j.service.confirmed.CreateObjectRequest;
import com.serotonin.bacnet4j.service.confirmed.DeleteObjectRequest;
import com.serotonin.bacnet4j.service.confirmed.DeviceCommunicationControlRequest;
import com.serotonin.bacnet4j.service.confirmed.DeviceCommunicationControlRequest.EnableDisable;
import com.serotonin.bacnet4j.service.confirmed.GetAlarmSummaryRequest;
import com.serotonin.bacnet4j.service.confirmed.GetEnrollmentSummaryRequest;
import com.serotonin.bacnet4j.service.confirmed.GetEnrollmentSummaryRequest.PriorityFilter;
import com.serotonin.bacnet4j.service.confirmed.GetEventInformationRequest;
import com.serotonin.bacnet4j.service.confirmed.LifeSafetyOperationRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadRangeRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadRangeRequest.ByTime;
import com.serotonin.bacnet4j.service.confirmed.ReinitializeDeviceRequest;
import com.serotonin.bacnet4j.service.confirmed.ReinitializeDeviceRequest.ReinitializedStateOfDevice;
import com.serotonin.bacnet4j.service.confirmed.RemoveListElementRequest;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVPropertyMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVPropertyMultipleRequest.CovSubscriptionSpecification;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVPropertyMultipleRequest.CovSubscriptionSpecification.CovReference;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVPropertyRequest;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVRequest;
import com.serotonin.bacnet4j.service.confirmed.VtCloseRequest;
import com.serotonin.bacnet4j.service.confirmed.VtDataRequest;
import com.serotonin.bacnet4j.service.confirmed.VtOpenRequest;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyRequest;
import com.serotonin.bacnet4j.service.unconfirmed.IAmRequest;
import com.serotonin.bacnet4j.service.unconfirmed.IHaveRequest;
import com.serotonin.bacnet4j.service.unconfirmed.TimeSynchronizationRequest;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedCovNotificationMultipleRequest;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedCovNotificationRequest;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedEventNotificationRequest;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedPrivateTransferRequest;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedRequestService;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedTextMessageRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WhoHasRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WriteGroupRequest;
import com.serotonin.bacnet4j.type.EncodedValue;
import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.ChannelValue;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.EventTransitionBits;
import com.serotonin.bacnet4j.type.constructed.GroupChannelValue;
import com.serotonin.bacnet4j.type.constructed.LogRecord;
import com.serotonin.bacnet4j.type.constructed.PropertyReference;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.ReadAccessResult;
import com.serotonin.bacnet4j.type.constructed.ReadAccessResult.Result;
import com.serotonin.bacnet4j.type.constructed.ReadAccessSpecification;
import com.serotonin.bacnet4j.type.constructed.Recipient;
import com.serotonin.bacnet4j.type.constructed.RecipientProcess;
import com.serotonin.bacnet4j.type.constructed.ResultFlags;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.ServicesSupported;
import com.serotonin.bacnet4j.type.constructed.StatusFlags;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.constructed.WriteAccessSpecification;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.FileAccessMethod;
import com.serotonin.bacnet4j.type.enumerated.LifeSafetyOperation;
import com.serotonin.bacnet4j.type.enumerated.MessagePriority;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.enumerated.Reliability;
import com.serotonin.bacnet4j.type.enumerated.Segmentation;
import com.serotonin.bacnet4j.type.enumerated.VtClass;
import com.serotonin.bacnet4j.type.error.BaseError;
import com.serotonin.bacnet4j.type.error.ErrorClassAndCode;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.notificationParameters.OutOfRangeNotif;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.Date;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.OctetString;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.SignedInteger;
import com.serotonin.bacnet4j.type.primitive.Time;
import com.serotonin.bacnet4j.type.primitive.Unsigned16;
import com.serotonin.bacnet4j.type.primitive.Unsigned32;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

public class AnnexFEncodingTest {
    private ServicesSupported servicesSupported;

    @Before
    public void before() {
        servicesSupported = new ServicesSupported();
        servicesSupported.setAll(true);
    }

    @Test
    public void e1_1aTest() {
        final AcknowledgeAlarmRequest acknowledgeAlarmRequest = new AcknowledgeAlarmRequest(new UnsignedInteger(1),
                new ObjectIdentifier(ObjectType.analogInput, 2), EventState.highLimit,
                new TimeStamp(new UnsignedInteger(16)),
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "MDL"),
                new TimeStamp(
                        new DateTime(new Date(1992, Month.JUNE, 21, DayOfWeek.UNSPECIFIED), new Time(13, 3, 41, 9))));

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 7, (byte) 0, 0, acknowledgeAlarmRequest);

        compare(pdu, "0002070009011c0000000229033e19103f4c004d444c5e2ea45c0615ffb40d0329092f5f");
    }

    @Test
    public void e1_1bTest() {
        final SimpleACK simpleACK = new SimpleACK((byte) 7, 0);
        compare(simpleACK, "200700");
    }

    @Test
    public void e1_2aTest() {
        final List<PropertyValue> list = new ArrayList<>();
        list.add(new PropertyValue(PropertyIdentifier.presentValue, null, new Real(65), null));
        list.add(new PropertyValue(PropertyIdentifier.statusFlags, null, new StatusFlags(false, false, false, false),
                null));

        final SequenceOf<PropertyValue> listOfValues = new SequenceOf<>(list);

        final ConfirmedCovNotificationRequest confirmedCovNotification = new ConfirmedCovNotificationRequest(
                new UnsignedInteger(18), new ObjectIdentifier(ObjectType.device, 4),
                new ObjectIdentifier(ObjectType.analogInput, 10), UnsignedInteger.ZERO, listOfValues);

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 15, (byte) 0, 0, confirmedCovNotification);

        compare(pdu, "00020f0109121c020000042c0000000a39004e09552e44428200002f096f2e8204002f4f");
    }

    @Test
    public void e1_2bTest() {
        final SimpleACK pdu = new SimpleACK((byte) 15, 1);
        compare(pdu, "200f01");
    }

    @Test
    public void e1_3Test() {
        final List<PropertyValue> list = new ArrayList<>();
        list.add(new PropertyValue(PropertyIdentifier.presentValue, null, new Real(65), null));
        list.add(new PropertyValue(PropertyIdentifier.statusFlags, null, new StatusFlags(false, false, false, false),
                null));

        final SequenceOf<PropertyValue> listOfValues = new SequenceOf<>(list);

        final UnconfirmedCovNotificationRequest unconfirmedCovNotificationRequest = new UnconfirmedCovNotificationRequest(
                new UnsignedInteger(18), new ObjectIdentifier(ObjectType.device, 4),
                new ObjectIdentifier(ObjectType.analogInput, 10), UnsignedInteger.ZERO, listOfValues);

        final UnconfirmedRequest pdu = new UnconfirmedRequest(unconfirmedCovNotificationRequest);

        compare(pdu, "100209121c020000042c0000000a39004e09552e44428200002f096f2e8204002f4f");
    }

    @Test
    public void e1_4aTest() {
        final ConfirmedEventNotificationRequest confirmedEventNotificationRequest = new ConfirmedEventNotificationRequest(
                new UnsignedInteger(1), new ObjectIdentifier(ObjectType.device, 4),
                new ObjectIdentifier(ObjectType.analogInput, 2), new TimeStamp(new UnsignedInteger(16)),
                new UnsignedInteger(4), new UnsignedInteger(100), EventType.outOfRange, null, NotifyType.alarm,
                Boolean.TRUE, EventState.normal, EventState.highLimit,
                new NotificationParameters(new OutOfRangeNotif(new Real(80.1f),
                        new StatusFlags(true, false, false, false), new Real(1), new Real(80))));

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 16, (byte) 0, 0, confirmedEventNotificationRequest);

        compare(pdu, "0002100209011c020000042c000000023e19103f49045964690589009901a900b903ce5e0c42a033331a0"
                + "4802c3f8000003c42a000005fcf");
    }

    @Test
    public void e1_4bTest() {
        final SimpleACK pdu = new SimpleACK((byte) 16, 2);
        compare(pdu, "201002");
    }

    @Test
    public void e1_4cTest() {
        final AcknowledgeAlarmRequest req = new AcknowledgeAlarmRequest(new UnsignedInteger(1),
                new ObjectIdentifier(ObjectType.analogInput, 2), EventState.highLimit,
                new TimeStamp(new UnsignedInteger(16)), new CharacterString("MDL"),
                new TimeStamp(new DateTime(new Date(1992, Month.JUNE, 21, null), new Time(13, 3, 41, 9))));

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 7, (byte) 0, 0, req);

        compare(pdu, "0002070009011c0000000229033e19103f4c004d444c5e2ea45c0615ffb40d0329092f5f");
    }

    @Test
    public void e1_4dTest() {
        final SimpleACK pdu = new SimpleACK((byte) 7, 0);
        compare(pdu, "200700");
    }

    @Test
    public void e1_5Test() {
        final UnconfirmedEventNotificationRequest unconfirmedEventNotificationRequest = new UnconfirmedEventNotificationRequest(
                new UnsignedInteger(1), new ObjectIdentifier(ObjectType.device, 9),
                new ObjectIdentifier(ObjectType.analogInput, 2), new TimeStamp(new UnsignedInteger(16)),
                new UnsignedInteger(4), new UnsignedInteger(100), EventType.outOfRange, null, NotifyType.alarm,
                Boolean.TRUE, EventState.normal, EventState.highLimit,
                new NotificationParameters(new OutOfRangeNotif(new Real(80.1f),
                        new StatusFlags(true, false, false, false), new Real(1), new Real(80))));

        final UnconfirmedRequest pdu = new UnconfirmedRequest(unconfirmedEventNotificationRequest);

        compare(pdu, "100309011c020000092c000000023e19103f49045964690589009901a900b903ce5e0c42a033331a048"
                + "02c3f8000003c42a000005fcf");
    }

    @Test
    public void e1_6aTest() {
        final GetAlarmSummaryRequest getAlarmSummaryRequest = new GetAlarmSummaryRequest();

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 1, (byte) 0, 0, getAlarmSummaryRequest);

        compare(pdu, "00020103");
    }

    @Test
    public void e1_6bTest() {
        final List<GetAlarmSummaryAck.AlarmSummary> alarmSummaries = new ArrayList<>();
        alarmSummaries.add(new GetAlarmSummaryAck.AlarmSummary(new ObjectIdentifier(ObjectType.analogInput, 2),
                EventState.highLimit, new EventTransitionBits(false, true, true)));
        alarmSummaries.add(new GetAlarmSummaryAck.AlarmSummary(new ObjectIdentifier(ObjectType.analogInput, 3),
                EventState.lowLimit, new EventTransitionBits(true, true, true)));

        final GetAlarmSummaryAck getAlarmSummaryAck = new GetAlarmSummaryAck(new SequenceOf<>(alarmSummaries));

        final ComplexACK pdu = new ComplexACK(false, false, (byte) 1, 0, 0, getAlarmSummaryAck);

        compare(pdu, "300103c4000000029103820560c40000000391048205e0");
    }

    @Test
    public void e1_7aTest() {
        final GetEnrollmentSummaryRequest getEnrollmentSummaryRequest = new GetEnrollmentSummaryRequest(
                GetEnrollmentSummaryRequest.AcknowledgmentFilter.notAcked, null, null, null, null, null);

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 1, (byte) 0, 0, getEnrollmentSummaryRequest);

        compare(pdu, "000201040902");
    }

    @Test
    public void e1_7bTest() {
        final List<GetEnrollmentSummaryAck.EnrollmentSummary> enrollmentSummaries = new ArrayList<>();
        enrollmentSummaries
                .add(new GetEnrollmentSummaryAck.EnrollmentSummary(new ObjectIdentifier(ObjectType.analogInput, 2),
                        EventType.outOfRange, EventState.highLimit, new UnsignedInteger(100), new UnsignedInteger(4)));
        enrollmentSummaries
                .add(new GetEnrollmentSummaryAck.EnrollmentSummary(new ObjectIdentifier(ObjectType.eventEnrollment, 6),
                        EventType.changeOfState, EventState.normal, new UnsignedInteger(50), new UnsignedInteger(2)));

        final GetEnrollmentSummaryAck getEnrollmentSummaryAck = new GetEnrollmentSummaryAck(
                new SequenceOf<>(enrollmentSummaries));

        final ComplexACK pdu = new ComplexACK(false, false, (byte) 1, 0, 0, getEnrollmentSummaryAck);

        compare(pdu, "300104c4000000029105910321642104c4024000069101910021322102");
    }

    @Test
    public void e1_7cTest() {
        final GetEnrollmentSummaryRequest getEnrollmentSummaryRequest = new GetEnrollmentSummaryRequest(
                GetEnrollmentSummaryRequest.AcknowledgmentFilter.all,
                new RecipientProcess(new Recipient(new ObjectIdentifier(ObjectType.device, 17)),
                        new UnsignedInteger(9)),
                null, null, new PriorityFilter(new UnsignedInteger(6), new UnsignedInteger(10)), null);

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 2, (byte) 0, 0, getEnrollmentSummaryRequest);

        compare(pdu, "0002020409001e0e0c020000110f19091f4e0906190a4f");
    }

    @Test
    public void e1_7dTest() {
        final List<GetEnrollmentSummaryAck.EnrollmentSummary> enrollmentSummaries = new ArrayList<>();
        enrollmentSummaries
                .add(new GetEnrollmentSummaryAck.EnrollmentSummary(new ObjectIdentifier(ObjectType.analogInput, 2),
                        EventType.outOfRange, EventState.normal, new UnsignedInteger(8), new UnsignedInteger(4)));
        enrollmentSummaries
                .add(new GetEnrollmentSummaryAck.EnrollmentSummary(new ObjectIdentifier(ObjectType.analogInput, 3),
                        EventType.outOfRange, EventState.normal, new UnsignedInteger(8), new UnsignedInteger(4)));
        enrollmentSummaries
                .add(new GetEnrollmentSummaryAck.EnrollmentSummary(new ObjectIdentifier(ObjectType.analogInput, 4),
                        EventType.outOfRange, EventState.normal, new UnsignedInteger(8), new UnsignedInteger(4)));
        enrollmentSummaries
                .add(new GetEnrollmentSummaryAck.EnrollmentSummary(new ObjectIdentifier(ObjectType.eventEnrollment, 7),
                        EventType.floatingLimit, EventState.normal, new UnsignedInteger(3), new UnsignedInteger(8)));

        final GetEnrollmentSummaryAck getEnrollmentSummaryAck = new GetEnrollmentSummaryAck(
                new SequenceOf<>(enrollmentSummaries));

        final ComplexACK pdu = new ComplexACK(false, false, (byte) 2, 0, 0, getEnrollmentSummaryAck);

        compare(pdu,
                "300204c4000000029105910021082104c4000000039105910021082104c4000000049105910021082104c4024000079104910021032108");
    }

    @Test
    public void e1_8aTest() {
        final GetEventInformationRequest getEventInformation = new GetEventInformationRequest(null);

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, true, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 1, (byte) 0, 0, getEventInformation);

        compare(pdu, "0202011d");
    }

    @Test
    public void e1_8bTest() {
        final List<GetEventInformationAck.EventSummary> eventSummaries = new ArrayList<>();
        eventSummaries.add(new GetEventInformationAck.EventSummary( //
                new ObjectIdentifier(ObjectType.analogInput, 2), //
                EventState.highLimit, //
                new EventTransitionBits(false, true, true), //
                new BACnetArray<>(new TimeStamp(new Time(15, 35, 0, 20)), new TimeStamp(new Time(255, 255, 255, 255)),
                        new TimeStamp(new Time(255, 255, 255, 255))), //
                NotifyType.alarm, //
                new EventTransitionBits(true, true, true), //
                new BACnetArray<>(new UnsignedInteger(15), new UnsignedInteger(15), new UnsignedInteger(20))));
        eventSummaries.add(new GetEventInformationAck.EventSummary( //
                new ObjectIdentifier(ObjectType.analogInput, 3), //
                EventState.normal, //
                new EventTransitionBits(true, true, false), //
                new BACnetArray<>(new TimeStamp(new Time(15, 40, 0, 0)), new TimeStamp(new Time(255, 255, 255, 255)),
                        new TimeStamp(new Time(15, 45, 30, 30))), //
                NotifyType.alarm, //
                new EventTransitionBits(true, true, true),
                new BACnetArray<>(new UnsignedInteger(15), new UnsignedInteger(15), new UnsignedInteger(20))));

        final GetEventInformationAck getEventInformationAck = new GetEventInformationAck(
                new SequenceOf<>(eventSummaries), Boolean.FALSE);

        final ComplexACK pdu = new ComplexACK(false, false, (byte) 1, 0, 0, getEventInformationAck);

        compare(pdu, "30011d0e0c0000000219032a05603e0c0f2300140cffffffff0cffffffff3f49005a05e06e210f210f21146f0"
                + "c0000000319002a05c03e0c0f2800000cffffffff0c0f2d1e1e3f49005a05e06e210f210f21146f0f1900");
    }

    @Test
    public void e1_9aTest() {
        final LifeSafetyOperationRequest lifeSafetyOperationRequest = new LifeSafetyOperationRequest(
                new UnsignedInteger(18), new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "MDL"),
                LifeSafetyOperation.reset, new ObjectIdentifier(ObjectType.lifeSafetyPoint, 1));

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 15, (byte) 0, 0, lifeSafetyOperationRequest);

        compare(pdu, "00020f1b09121c004d444c29043c05400001");
    }

    @Test
    public void e1_9bTest() {
        final SimpleACK pdu = new SimpleACK((byte) 15, 27);
        compare(pdu, "200f1b");
    }

    @Test
    public void e1_10aTest() {
        final SubscribeCOVRequest subscribeCOVRequest = new SubscribeCOVRequest(new UnsignedInteger(18),
                new ObjectIdentifier(ObjectType.analogInput, 10), Boolean.TRUE, UnsignedInteger.ZERO);

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 15, (byte) 0, 0, subscribeCOVRequest);

        compare(pdu, "00020f0509121c0000000a29013900");
    }

    @Test
    public void e1_10bTest() {
        final SimpleACK pdu = new SimpleACK((byte) 15, 5);
        compare(pdu, "200f05");
    }

    @Test
    public void e1_11aTest() {
        final SubscribeCOVPropertyRequest subscribeCOVPropertyRequest = new SubscribeCOVPropertyRequest(
                new UnsignedInteger(18), new ObjectIdentifier(ObjectType.analogInput, 10), Boolean.TRUE,
                new UnsignedInteger(60), new PropertyReference(PropertyIdentifier.presentValue, null), new Real(1));

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 15, (byte) 0, 0, subscribeCOVPropertyRequest);

        compare(pdu, "00020f1c09121c0000000a2901393c4e09554f5c3f800000");
    }

    @Test
    public void e1_11bTest() {
        final SimpleACK pdu = new SimpleACK((byte) 15, 28);
        compare(pdu, "200f1c");
    }

    @Test
    public void e1_12aTest() {
        final SubscribeCOVPropertyMultipleRequest req = new SubscribeCOVPropertyMultipleRequest(new Unsigned32(18),
                Boolean.TRUE,
                new UnsignedInteger(
                        60),
                new UnsignedInteger(5),
                new SequenceOf<>(
                        new CovSubscriptionSpecification(new ObjectIdentifier(ObjectType.analogInput, 10),
                                new SequenceOf<>(
                                        new CovReference(new PropertyReference(PropertyIdentifier.presentValue),
                                                new Real(1), Boolean.TRUE),
                                        new CovReference(new PropertyReference(PropertyIdentifier.reliability), null,
                                                Boolean.FALSE))),
                        new CovSubscriptionSpecification(new ObjectIdentifier(ObjectType.analogOutput, 8),
                                new SequenceOf<>(
                                        new CovReference(new PropertyReference(PropertyIdentifier.presentValue),
                                                new Real(1), Boolean.TRUE)))));

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 15, (byte) 0, 0, req);

        compare(pdu, "00020f1e09121901293c39054e0c0000000a1e0e09550f1c3f80000029010e09670f29001f0c004000081e0e" + ""
                + "09550f1c3f80000029011f4f");
    }

    @Test
    public void e1_12bTest() {
        final SimpleACK pdu = new SimpleACK((byte) 15, 30);
        compare(pdu, "200f1e");
    }

    @Test
    public void e1_13aTest() {
        final ConfirmedCovNotificationMultipleRequest req = new ConfirmedCovNotificationMultipleRequest(
                new Unsigned32(18), new ObjectIdentifier(ObjectType.device, 4), new UnsignedInteger(35),
                new DateTime(new Date(2013, Month.JUNE, 3, DayOfWeek.MONDAY), new Time(3, 23, 53, 47)),
                new SequenceOf<>(
                        new CovNotification(new ObjectIdentifier(ObjectType.analogInput, 10),
                                new SequenceOf<>(new CovNotificationValue(PropertyIdentifier.presentValue, null,
                                        new Real(65), new Time(3, 23, 52, 0)))),
                        new CovNotification(new ObjectIdentifier(ObjectType.analogOutput, 5),
                                new SequenceOf<>(new CovNotificationValue(PropertyIdentifier.presentValue, null,
                                        new Real(80.1F), null)))));

        final ConfirmedRequest pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED,
                MaxApduLength.UP_TO_206, (byte) 15, (byte) 0, 0, req);

        // TODO
        // There are multiple differences with the spec here.
        // 1) ---------------------------27
        // 2) -----------------------------------------------------------------------------------2e
        compare(pdu,
                "00020f1f09121c0200000429233ea471060301b40317352f3f4e0c0000000a1e09552e44428200002f3c03173400"
                        // 3) ---------------------------2e
                        + "1f0c004000051e09552e4442a033332f1f4f");
    }

    @Test
    public void e1_13bTest() {
        final SimpleACK pdu = new SimpleACK((byte) 15, 31);
        compare(pdu, "200f1f");
    }

    @Test
    public void e1_14aTest() {
        // TODO the spec appears to be really messed up with this one, putting an unconfirmed service into
        // a confirmed request
        final UnconfirmedCovNotificationMultipleRequest req = new UnconfirmedCovNotificationMultipleRequest(
                new Unsigned32(18), new ObjectIdentifier(ObjectType.device, 4), new UnsignedInteger(27), null,
                new SequenceOf<>(
                        new com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedCovNotificationMultipleRequest.CovNotification(
                                new ObjectIdentifier(ObjectType.analogInput, 10), new SequenceOf<>(
                                        new com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedCovNotificationMultipleRequest.CovNotification.CovNotificationValue(
                                                PropertyIdentifier.presentValue, null, new Real(65), null)))));

        final UnconfirmedRequest pdu = new UnconfirmedRequest(req);

        compare(pdu, "100b09121c02000004291b4e0c0000000a1e09552e44428200002f1f4f");
    }

    @Test
    public void e2_1aTest() {
        final ConfirmedRequestService service = new AtomicReadFileRequest(new ObjectIdentifier(ObjectType.file, 1),
                new StreamAccess(new SignedInteger(0), new UnsignedInteger(27)));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_206,
                (byte) 0, (byte) 0, 0, service);
        compare(pdu, "00020006C4028000010E3100211B0F");
    }

    @Test
    public void e2_1bTest() {
        final AcknowledgementService service = new AtomicReadFileAck(Boolean.FALSE,
                new StreamAccessAck(new SignedInteger(0), new OctetString("Chiller01 On-Time=4.3 Hours".getBytes())));
        final APDU pdu = new ComplexACK(false, false, (byte) 0, 0, 0, service);
        compare(pdu, "300006100E3100651B4368696C6C65723031204F6E2D54696D653D342E3320486F7572730F");
    }

    @Test
    public void e2_1cTest() {
        final ConfirmedRequestService service = new AtomicReadFileRequest(new ObjectIdentifier(ObjectType.file, 2),
                new RecordAccess(new SignedInteger(14), new UnsignedInteger(3)));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_206,
                (byte) 18, (byte) 0, 0, service);
        compare(pdu, "00021206C4028000021E310E21031F");
    }

    @Test
    public void e2_1dTest() {
        final List<OctetString> strings = new ArrayList<>();
        strings.add(new OctetString("12:00,45.6".getBytes()));
        strings.add(new OctetString("12:15,44.8".getBytes()));
        final AcknowledgementService service = new AtomicReadFileAck(Boolean.TRUE,
                new RecordAccessAck(new SignedInteger(14), new UnsignedInteger(2), new SequenceOf<>(strings)));
        final APDU pdu = new ComplexACK(false, false, (byte) 18, 0, 0, service);
        compare(pdu, "301206111E310E2102650A31323A30302C34352E36650A31323A31352C34342E381F");
    }

    @Test
    public void e2_2aTest() {
        final ConfirmedRequestService service = new AtomicWriteFileRequest(new ObjectIdentifier(ObjectType.file, 1),
                new com.serotonin.bacnet4j.service.confirmed.AtomicWriteFileRequest.StreamAccess(new SignedInteger(30),
                        new OctetString("Chiller01 On-Time=4.3 Hours".getBytes())));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_206,
                (byte) 85, (byte) 0, 0, service);
        compare(pdu, "00025507C4028000010E311E651B4368696C6C65723031204F6E2D54696D653D342E3320486F7572730F");
    }

    @Test
    public void e2_2bTest() {
        final AcknowledgementService service = new AtomicWriteFileAck(false, new SignedInteger(30));
        final APDU pdu = new ComplexACK(false, false, (byte) 85, 0, 0, service);
        compare(pdu, "305507091E");
    }

    @Test
    public void e2_2cTest() {
        final List<OctetString> strings = new ArrayList<>();
        strings.add(new OctetString("12:00,45.6".getBytes()));
        strings.add(new OctetString("12:15,44.8".getBytes()));
        final ConfirmedRequestService service = new AtomicWriteFileRequest(new ObjectIdentifier(ObjectType.file, 2),
                new com.serotonin.bacnet4j.service.confirmed.AtomicWriteFileRequest.RecordAccess(new SignedInteger(-1),
                        new UnsignedInteger(2), new SequenceOf<>(strings)));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_206,
                (byte) 85, (byte) 0, 0, service);
        compare(pdu, "00025507C4028000021E31FF2102650A31323A30302C34352E36650A31323A31352C34342E381F");
    }

    @Test
    public void e2_2dTest() {
        final AcknowledgementService service = new AtomicWriteFileAck(true, new SignedInteger(14));
        final APDU pdu = new ComplexACK(false, false, (byte) 85, 0, 0, service);
        compare(pdu, "305507190E");
    }

    @Test
    public void e3_1aTest() {
        final List<PropertyReference> propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        propertyReferences.add(new PropertyReference(PropertyIdentifier.reliability, null));

        final List<ReadAccessSpecification> elements = new ArrayList<>();
        elements.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 15),
                new SequenceOf<>(propertyReferences)));
        final ConfirmedRequestService service = new AddListElementRequest(new ObjectIdentifier(ObjectType.group, 3),
                PropertyIdentifier.listOfGroupMembers, null, new SequenceOf<>(elements));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_206,
                (byte) 1, (byte) 0, 0, service);
        compare(pdu, "000201080C02C0000319353E0C0000000F1E095509671F3F");
    }

    @Test
    public void e3_1bTest() {
        final APDU pdu = new SimpleACK((byte) 1, 8);
        compare(pdu, "200108");
    }

    @Test
    public void e3_2aTest() {
        final List<ReadAccessSpecification> readAccessSpecs = new ArrayList<>();

        List<PropertyReference> propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        propertyReferences.add(new PropertyReference(PropertyIdentifier.reliability, null));
        propertyReferences.add(new PropertyReference(PropertyIdentifier.description, null));
        readAccessSpecs.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 12),
                new SequenceOf<>(propertyReferences)));

        propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        propertyReferences.add(new PropertyReference(PropertyIdentifier.reliability, null));
        propertyReferences.add(new PropertyReference(PropertyIdentifier.description, null));
        readAccessSpecs.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 13),
                new SequenceOf<>(propertyReferences)));

        final ConfirmedRequestService service = new RemoveListElementRequest(new ObjectIdentifier(ObjectType.group, 3),
                PropertyIdentifier.listOfGroupMembers, null, new SequenceOf<>(readAccessSpecs));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_206,
                (byte) 52, (byte) 0, 0, service);
        compare(pdu, "000234090C02C0000319353E0C0000000C1E09550967091C1F0C0000000D1E09550967091C1F3F");
    }

    @Test
    public void e3_2bTest() {
        final APDU pdu = new SimpleACK((byte) 52, 9);
        compare(pdu, "203409");
    }

    @Test
    public void e3_2cTest() {
        final List<ReadAccessSpecification> readAccessSpecs = new ArrayList<>();

        List<PropertyReference> propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        propertyReferences.add(new PropertyReference(PropertyIdentifier.reliability, null));
        readAccessSpecs.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 12),
                new SequenceOf<>(propertyReferences)));

        propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        propertyReferences.add(new PropertyReference(PropertyIdentifier.reliability, null));
        readAccessSpecs.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 13),
                new SequenceOf<>(propertyReferences)));

        final ConfirmedRequestService service = new AddListElementRequest(new ObjectIdentifier(ObjectType.group, 3),
                PropertyIdentifier.listOfGroupMembers, null, new SequenceOf<>(readAccessSpecs));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_206,
                (byte) 53, (byte) 0, 0, service);
        compare(pdu, "000235080C02C0000319353E0C0000000C1E095509671F0C0000000D1E095509671F3F");
    }

    @Test
    public void e3_2dTest() {
        final APDU pdu = new SimpleACK((byte) 53, 8);
        compare(pdu, "203508");
    }

    @Test
    public void e3_3aTest() {
        final List<PropertyValue> propertyValues = new ArrayList<>();
        propertyValues.add(new PropertyValue(PropertyIdentifier.objectName, null,
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "Trend 1"), null));
        propertyValues
                .add(new PropertyValue(PropertyIdentifier.fileAccessMethod, null, FileAccessMethod.recordAccess, null));
        final ConfirmedRequestService service = new CreateObjectRequest(ObjectType.file,
                new SequenceOf<>(propertyValues));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 86, (byte) 0, 0, service);
        compare(pdu, "0004560A0E090A0F1E094D2E7508005472656E6420312F09292E91002F1F");
    }

    @Test
    public void e3_3bTest() {
        final AcknowledgementService service = new CreateObjectAck(new ObjectIdentifier(ObjectType.file, 13));
        final APDU pdu = new ComplexACK(false, false, (byte) 86, 0, 0, service);
        compare(pdu, "30560AC40280000D");
    }

    @Test
    public void e3_4aTest() {
        final ConfirmedRequestService service = new DeleteObjectRequest(new ObjectIdentifier(ObjectType.group, 6));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 87, (byte) 0, 0, service);
        compare(pdu, "0004570BC402C00006");
    }

    @Test
    public void e3_4bTest() {
        final APDU pdu = new SimpleACK((byte) 87, 11);
        compare(pdu, "20570B");
    }

    @Test
    public void e3_4cTest() {
        final ConfirmedRequestService service = new DeleteObjectRequest(new ObjectIdentifier(ObjectType.group, 7));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 88, (byte) 0, 0, service);
        compare(pdu, "0004580BC402C00007");
    }

    @Test
    public void e3_4dTest() {
        final BaseError baseError = new ErrorClassAndCode(ErrorClass.object, ErrorCode.objectDeletionNotPermitted);
        final APDU pdu = new com.serotonin.bacnet4j.apdu.Error((byte) 88, 11, baseError);
        compare(pdu, "50580B91019117");
    }

    @Test
    public void e3_5aTest() {
        final ConfirmedRequestService service = new ReadPropertyRequest(new ObjectIdentifier(ObjectType.analogInput, 5),
                PropertyIdentifier.presentValue, null);
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_50,
                (byte) 1, (byte) 0, 0, service);
        compare(pdu, "0000010C0C000000051955");
    }

    @Test
    public void e3_5bTest() {
        final AcknowledgementService service = new ReadPropertyAck(new ObjectIdentifier(ObjectType.analogInput, 5),
                PropertyIdentifier.presentValue, null, new Real(72.3f));
        final APDU pdu = new ComplexACK(false, false, (byte) 1, 0, 0, service);
        compare(pdu, "30010C0C0000000519553E444290999A3F");
    }

    @Test
    public void e3_7aTest() {
        final List<ReadAccessSpecification> readAccessSpecs = new ArrayList<>();
        final List<PropertyReference> propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        propertyReferences.add(new PropertyReference(PropertyIdentifier.reliability, null));
        readAccessSpecs.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 16),
                new SequenceOf<>(propertyReferences)));
        final ConfirmedRequestService service = new ReadPropertyMultipleRequest(new SequenceOf<>(readAccessSpecs));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 241, (byte) 0, 0, service);
        compare(pdu, "0004F10E0C000000101E095509671F");
    }

    @Test
    public void e3_7bTest() {
        final List<ReadAccessResult> readAccessResults = new ArrayList<>();
        final List<Result> results = new ArrayList<>();
        results.add(new Result(PropertyIdentifier.presentValue, null, new Real(72.3f)));
        results.add(new Result(PropertyIdentifier.reliability, null, Reliability.noFaultDetected));
        readAccessResults
                .add(new ReadAccessResult(new ObjectIdentifier(ObjectType.analogInput, 16), new SequenceOf<>(results)));
        final AcknowledgementService service = new ReadPropertyMultipleAck(new SequenceOf<>(readAccessResults));
        final APDU pdu = new ComplexACK(false, false, (byte) 241, 0, 0, service);
        compare(pdu, "30F10E0C000000101E29554E444290999A4F29674E91004F1F");
    }

    @Test
    public void e3_7cTest() {
        final List<ReadAccessSpecification> readAccessSpecs = new ArrayList<>();

        List<PropertyReference> propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        readAccessSpecs.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 33),
                new SequenceOf<>(propertyReferences)));

        propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        readAccessSpecs.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 50),
                new SequenceOf<>(propertyReferences)));

        propertyReferences = new ArrayList<>();
        propertyReferences.add(new PropertyReference(PropertyIdentifier.presentValue, null));
        readAccessSpecs.add(new ReadAccessSpecification(new ObjectIdentifier(ObjectType.analogInput, 35),
                new SequenceOf<>(propertyReferences)));

        final ConfirmedRequestService service = new ReadPropertyMultipleRequest(new SequenceOf<>(readAccessSpecs));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 2, (byte) 0, 0, service);
        compare(pdu, "0004020E0C000000211E09551F0C000000321E09551F0C000000231E09551F");
    }

    @Test
    public void e3_7dTest() {
        final List<ReadAccessResult> readAccessResults = new ArrayList<>();

        List<Result> results = new ArrayList<>();
        results.add(new Result(PropertyIdentifier.presentValue, null, new Real(42.3f)));
        readAccessResults
                .add(new ReadAccessResult(new ObjectIdentifier(ObjectType.analogInput, 33), new SequenceOf<>(results)));

        results = new ArrayList<>();
        results.add(new Result(PropertyIdentifier.presentValue, null,
                new ErrorClassAndCode(ErrorClass.object, ErrorCode.unknownObject)));
        readAccessResults
                .add(new ReadAccessResult(new ObjectIdentifier(ObjectType.analogInput, 50), new SequenceOf<>(results)));

        results = new ArrayList<>();
        results.add(new Result(PropertyIdentifier.presentValue, null, new Real(435.7f)));
        readAccessResults
                .add(new ReadAccessResult(new ObjectIdentifier(ObjectType.analogInput, 35), new SequenceOf<>(results)));

        final AcknowledgementService service = new ReadPropertyMultipleAck(new SequenceOf<>(readAccessResults));
        final APDU pdu = new ComplexACK(false, false, (byte) 2, 0, 0, service);
        compare(pdu,
                "30020E0C000000211E29554E44422933334F1F0C000000321E29555E9101911F5F1F0C000000231E29554E4443D9D99A4F1F");
    }

    @Test
    public void e3_8aTest() {
        final ReadRangeRequest req = new ReadRangeRequest(new ObjectIdentifier(ObjectType.trendLog, 1),
                PropertyIdentifier.logBuffer, null,
                new ByTime(new DateTime(new Date(1998, Month.MARCH, 23, DayOfWeek.MONDAY), new Time(19, 52, 34, 0)),
                        new SignedInteger(4)));
        final APDU pdu = new ConfirmedRequest(false, false, true, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_206,
                (byte) 1, (byte) 0, 0, req);
        compare(pdu, "0202011a0c0500000119837ea462031701b41334220031047f");
    }

    @Test
    public void e3_8bTest() {
        final AcknowledgementService service = new ReadRangeAck(new ObjectIdentifier(ObjectType.trendLog, 1),
                PropertyIdentifier.logBuffer, null, new ResultFlags(true, true, false), new UnsignedInteger(2),
                new SequenceOf<>(
                        new LogRecord(
                                new DateTime(new Date(1998, Month.MARCH, 23, DayOfWeek.MONDAY),
                                        new Time(19, 54, 27, 0)),
                                false, new Real(18), new StatusFlags(false, false, false, false)),
                        new LogRecord(
                                new DateTime(new Date(1998, Month.MARCH, 23, DayOfWeek.MONDAY),
                                        new Time(19, 56, 27, 0)),
                                false, new Real(18.1f), new StatusFlags(false, false, false, false))),
                new UnsignedInteger(79201));

        final APDU pdu = new ComplexACK(false, false, (byte) 1, 0, 0, service);

        compare(pdu, "30011a0c0500000119833a05c049025e0ea462031701b413361b000f1e2c419000001f2a04000ea462031701" + ""
                + "b413381b000f1e2c4190cccd1f2a04005f6b013561");
    }

    @Test
    public void e3_9aTest() {
        final ConfirmedRequestService service = new WritePropertyRequest(
                new ObjectIdentifier(ObjectType.analogValue, 1), PropertyIdentifier.presentValue, null, new Real(180),
                null);
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 89, (byte) 0, 0, service);
        compare(pdu, "0004590F0C0080000119553E44433400003F");
    }

    @Test
    public void e3_9bTest() {
        final APDU pdu = new SimpleACK((byte) 89, 15);
        compare(pdu, "20590F");
    }

    @Test
    public void e3_10aTest() {
        final List<WriteAccessSpecification> writeAccessSpecs = new ArrayList<>();

        List<PropertyValue> propertyValues = new ArrayList<>();
        propertyValues.add(new PropertyValue(PropertyIdentifier.presentValue, null, new Real(67), null));
        writeAccessSpecs.add(new WriteAccessSpecification(new ObjectIdentifier(ObjectType.analogValue, 5),
                new SequenceOf<>(propertyValues)));

        propertyValues = new ArrayList<>();
        propertyValues.add(new PropertyValue(PropertyIdentifier.presentValue, null, new Real(67), null));
        writeAccessSpecs.add(new WriteAccessSpecification(new ObjectIdentifier(ObjectType.analogValue, 6),
                new SequenceOf<>(propertyValues)));

        propertyValues = new ArrayList<>();
        propertyValues.add(new PropertyValue(PropertyIdentifier.presentValue, null, new Real(72), null));
        writeAccessSpecs.add(new WriteAccessSpecification(new ObjectIdentifier(ObjectType.analogValue, 7),
                new SequenceOf<>(propertyValues)));
        final ConfirmedRequestService service = new WritePropertyMultipleRequest(new SequenceOf<>(writeAccessSpecs));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 1, (byte) 0, 0, service);
        compare(pdu, "000401100C008000051E09552E44428600002F1F0C008000061E09552E44428600002F1F0C008000071E09552E4442"
                + "9000002F1F");
    }

    @Test
    public void e3_10bTest() {
        final APDU pdu = new SimpleACK((byte) 1, 16);
        compare(pdu, "200110");
    }

    @Test
    public void e3_11Test() {
        final WriteGroupRequest req = new WriteGroupRequest(new Unsigned32(23), new UnsignedInteger(8),
                new SequenceOf<>(
                        new GroupChannelValue(new Unsigned16(268), null, new ChannelValue(new UnsignedInteger(1111))),
                        new GroupChannelValue(new Unsigned16(269), null, new ChannelValue(new UnsignedInteger(2222)))),
                null);
        compare(new UnconfirmedRequest(req), "100a091719082e0a010c2204570a010d2208ae2f");
    }

    @Test
    public void e3_12Test() {
        final WriteGroupRequest req = new WriteGroupRequest(new Unsigned32(23), new UnsignedInteger(8),
                new SequenceOf<>(new GroupChannelValue(new Unsigned16(12), null, new ChannelValue(new Real(67))),
                        new GroupChannelValue(new Unsigned16(13), null, new ChannelValue(new Real(72)))),
                Boolean.TRUE);
        compare(new UnconfirmedRequest(req), "100a091719082e090c4442860000090d44429000002f3901");
    }

    @Test
    public void e3_13Test() {
        final WriteGroupRequest req = new WriteGroupRequest(new Unsigned32(23), new UnsignedInteger(8),
                new SequenceOf<>(
                        new GroupChannelValue(new Unsigned16(12), null, new ChannelValue(new UnsignedInteger(1111))),
                        new GroupChannelValue(new Unsigned16(13), new UnsignedInteger(10),
                                new ChannelValue(new CharacterString("ABC")))),
                null);
        compare(new UnconfirmedRequest(req), "100a091719082e090c220457090d190a74004142432f");
    }

    @Test
    public void e4_1aTest() {
        final ConfirmedRequestService service = new DeviceCommunicationControlRequest(new UnsignedInteger(5),
                EnableDisable.disable, new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "#egbdf!"));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 5, (byte) 0, 0, service);
        compare(pdu, "00040511090519012D080023656762646621");
    }

    @Test
    public void e4_1bTest() {
        final APDU pdu = new SimpleACK((byte) 5, 17);
        compare(pdu, "200511");
    }

    @Test
    public void e4_2aTest() {
        final EncodedValue parameters = new EncodedValue(new Real(72.4f), new OctetString(new byte[] { 0x16, 0x49 }));
        final ConfirmedRequestService service = new ConfirmedPrivateTransferRequest(new UnsignedInteger(25),
                new UnsignedInteger(8), parameters);
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_1024,
                (byte) 85, (byte) 0, 0, service);
        compare(pdu, "00045512091919082E444290CCCD6216492F");
    }

    @Test
    public void e4_2bTest() {
        final AcknowledgementService service = new ConfirmedPrivateTransferAck(new UnsignedInteger(25),
                new UnsignedInteger(8), null);
        final APDU pdu = new ComplexACK(false, false, (byte) 85, 0, 0, service);
        compare(pdu, "30551209191908");
    }

    @Test
    public void e4_3Test() {
        final EncodedValue parameters = new EncodedValue(new Real(72.4f), new OctetString(new byte[] { 0x16, 0x49 }));
        final UnconfirmedRequestService service = new UnconfirmedPrivateTransferRequest(new UnsignedInteger(25),
                new UnsignedInteger(8), parameters);
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1004091919082E444290CCCD6216492F");
    }

    @Test
    public void e4_4aTest() {
        final ConfirmedRequestService service = new ReinitializeDeviceRequest(ReinitializedStateOfDevice.warmstart,
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "AbCdEfGh"));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_128,
                (byte) 2, (byte) 0, 0, service);
        compare(pdu, "0001021409011D09004162436445664768");
    }

    @Test
    public void e4_4bTest() {
        final APDU pdu = new SimpleACK((byte) 2, 20);
        compare(pdu, "200214");
    }

    @Test
    public void e4_5aTest() {
        final ConfirmedRequestService service = new ConfirmedTextMessageRequest(
                new ObjectIdentifier(ObjectType.device, 5), MessagePriority.normal,
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "PM required for PUMP347"));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_128,
                (byte) 3, (byte) 0, 0, service);
        compare(pdu, "000103130C0200000529003D1800504D20726571756972656420666F722050554D50333437");
    }

    @Test
    public void e4_5bTest() {
        final APDU pdu = new SimpleACK((byte) 3, 19);
        compare(pdu, "200313");
    }

    @Test
    public void e4_6Test() {
        final UnconfirmedRequestService service = new UnconfirmedTextMessageRequest(
                new ObjectIdentifier(ObjectType.device, 5), MessagePriority.normal,
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "PM required for PUMP347"));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "10050C0200000529003D1800504D20726571756972656420666F722050554D50333437");
    }

    @Test
    public void e4_7Test() {
        final UnconfirmedRequestService service = new TimeSynchronizationRequest(
                new DateTime(new Date(1992, Month.NOVEMBER, 17, DayOfWeek.UNSPECIFIED), new Time(22, 45, 30, 70)));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1006A45C0B11FFB4162D1E46");
    }

    @Test
    public void e4_8aTest() {
        final UnconfirmedRequestService service = new WhoHasRequest(null,
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "OATemp"));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "10073D07004F4154656D70");
    }

    @Test
    public void e4_8bTest() {
        final UnconfirmedRequestService service = new IHaveRequest(new ObjectIdentifier(ObjectType.device, 8),
                new ObjectIdentifier(ObjectType.analogInput, 3),
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "OATemp"));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1001C402000008C4000000037507004F4154656D70");
    }

    @Test
    public void e4_8cTest() {
        final UnconfirmedRequestService service = new WhoHasRequest(null,
                new ObjectIdentifier(ObjectType.analogInput, 3));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "10072C00000003");
    }

    @Test
    public void e4_8dTest() {
        final UnconfirmedRequestService service = new IHaveRequest(new ObjectIdentifier(ObjectType.device, 8),
                new ObjectIdentifier(ObjectType.analogInput, 3),
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "OATemp"));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1001C402000008C4000000037507004F4154656D70");
    }

    @Test
    public void e4_9aTest() {
        final UnconfirmedRequestService service = new WhoIsRequest(new UnsignedInteger(3), new UnsignedInteger(3));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "100809031903");
    }

    @Test
    public void e4_9bTest() {
        final UnconfirmedRequestService service = new IAmRequest(new ObjectIdentifier(ObjectType.device, 3),
                new UnsignedInteger(1024), Segmentation.noSegmentation, new UnsignedInteger(99));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1000C40200000322040091032163");
    }

    @Test
    public void e4_9cTest() {
        final UnconfirmedRequestService service = new WhoIsRequest();
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1008");
    }

    @Test
    public void e4_9dTest() {
        final UnconfirmedRequestService service = new IAmRequest(new ObjectIdentifier(ObjectType.device, 1),
                new UnsignedInteger(480), Segmentation.segmentedTransmit, new UnsignedInteger(99));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1000C4020000012201E091012163");
    }

    @Test
    public void e4_9eTest() {
        final UnconfirmedRequestService service = new IAmRequest(new ObjectIdentifier(ObjectType.device, 2),
                new UnsignedInteger(206), Segmentation.segmentedReceive, new UnsignedInteger(33));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1000C40200000221CE91022121");
    }

    @Test
    public void e4_9fTest() {
        final UnconfirmedRequestService service = new IAmRequest(new ObjectIdentifier(ObjectType.device, 3),
                new UnsignedInteger(1024), Segmentation.noSegmentation, new UnsignedInteger(99));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1000C40200000322040091032163");
    }

    @Test
    public void e4_9gTest() {
        final UnconfirmedRequestService service = new IAmRequest(new ObjectIdentifier(ObjectType.device, 4),
                new UnsignedInteger(128), Segmentation.segmentedBoth, new UnsignedInteger(66));
        final APDU pdu = new UnconfirmedRequest(service);
        compare(pdu, "1000C402000004218091002142");
    }

    @Test
    public void e5_aTest() {
        final ConfirmedRequestService service = new VtOpenRequest(VtClass.ansi_x3_64, new UnsignedInteger(5));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_128,
                (byte) 80, (byte) 0, 0, service);
        compare(pdu, "0001501591012105");
    }

    @Test
    public void e5_bTest() {
        final AcknowledgementService service = new VtOpenAck(new UnsignedInteger(29));
        final APDU pdu = new ComplexACK(false, false, (byte) 80, 0, 0, service);
        compare(pdu, "305015211D");
    }

    @Test
    public void e5_cTest() {
        final byte[] data = "\r\nEnter User Name:".getBytes();
        final ConfirmedRequestService service = new VtDataRequest(new UnsignedInteger(5), new OctetString(data),
                UnsignedInteger.ZERO);
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_128,
                (byte) 81, (byte) 0, 0, service);
        compare(pdu, "00015117210565120D0A456E7465722055736572204E616D653A2100");
    }

    @Test
    public void e5_dTest() {
        final AcknowledgementService service = new VtDataAck(Boolean.TRUE, null);
        final APDU pdu = new ComplexACK(false, false, (byte) 81, 0, 0, service);
        compare(pdu, "3051170901");
    }

    @Test
    public void e5_eTest() {
        final ConfirmedRequestService service = new VtDataRequest(new UnsignedInteger(29),
                new OctetString("FRED\r".getBytes()), UnsignedInteger.ZERO);
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_128,
                (byte) 82, (byte) 0, 0, service);
        compare(pdu, "00015217211D6505465245440D2100");
    }

    @Test
    public void e5_fTest() {
        final AcknowledgementService service = new VtDataAck(Boolean.TRUE, null);
        final APDU pdu = new ComplexACK(false, false, (byte) 82, 0, 0, service);
        compare(pdu, "3052170901");
    }

    @Test
    public void e5_gTest() {
        final ConfirmedRequestService service = new VtDataRequest(new UnsignedInteger(5),
                new OctetString("FRED\r\nEnter Password:".getBytes()), new UnsignedInteger(1));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_128,
                (byte) 83, (byte) 0, 0, service);
        compare(pdu, "0001531721056515465245440D0A456E7465722050617373776F72643A2101");
    }

    @Test
    public void e5_hTest() {
        final AcknowledgementService service = new VtDataAck(Boolean.TRUE, null);
        final APDU pdu = new ComplexACK(false, false, (byte) 83, 0, 0, service);
        compare(pdu, "3053170901");
    }

    @Test
    public void e5_iTest() {
        final List<UnsignedInteger> ids = new ArrayList<>();
        ids.add(new UnsignedInteger(29));
        final ConfirmedRequestService service = new VtCloseRequest(new SequenceOf<>(ids));
        final APDU pdu = new ConfirmedRequest(false, false, false, MaxSegments.UNSPECIFIED, MaxApduLength.UP_TO_128,
                (byte) 84, (byte) 0, 0, service);
        compare(pdu, "00015416211D");
    }

    @Test
    public void e5_jTest() {
        final APDU pdu = new SimpleACK((byte) 84, 22);
        compare(pdu, "205416");
    }

    private void compare(final APDU pdu, final String expectedHex) {
        final ByteQueue expectedResult = new ByteQueue(expectedHex);

        // Serialize the APDU and compare with the hex.
        final ByteQueue queue = new ByteQueue();
        pdu.write(queue);
        assertEquals(expectedResult, queue);

        // Parse the hex and confirm the objects are equal.
        APDU parsedAPDU;
        try {
            parsedAPDU = APDU.createAPDU(servicesSupported, queue);
            if (parsedAPDU instanceof Segmentable)
                ((Segmentable) parsedAPDU).parseServiceData();
            if (parsedAPDU instanceof UnconfirmedRequest)
                ((UnconfirmedRequest) parsedAPDU).parseServiceData();
        } catch (final BACnetException e) {
            e.printStackTrace();
            fail(e.getMessage());
            return;
        }

        assertEquals(0, queue.size());

        if (!parsedAPDU.equals(pdu)) {
            parsedAPDU.equals(pdu); // For debugging
            parsedAPDU.equals(pdu);
            parsedAPDU.equals(pdu);
            throw new RuntimeException("Parsed APDU does not equal given APDU");
        }
    }
}
