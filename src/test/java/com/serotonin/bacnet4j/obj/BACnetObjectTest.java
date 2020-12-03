package com.serotonin.bacnet4j.obj;

import static org.junit.Assert.fail;

import java.math.BigInteger;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.AbstractTest;
import com.serotonin.bacnet4j.TestUtils;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.NameValue;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.Recipient;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.BitString;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.SignedInteger;
import com.serotonin.bacnet4j.type.primitive.Unsigned32;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings;
import com.serotonin.bacnet4j.util.RequestUtils;

public class BACnetObjectTest extends AbstractTest {
    static final Logger LOG = LoggerFactory.getLogger(BACnetObjectTest.class);

    @Override
    public void afterInit() throws Exception {
        d2.writePropertyInternal(PropertyIdentifier.tags,
                new BACnetArray<>( //
                        new NameValue("tag1", DateTime.UNSPECIFIED), //
                        new NameValue("tag2", new Real(234)), //
                        new NameValue("tag2", Null.instance)));
        d2.writePropertyInternal(PropertyIdentifier.forId(6789),
                new BACnetArray<>(new Real(0), new Real(1), new Real(2)));
        d2.writePropertyInternal(PropertyIdentifier.protocolVersion,
                new CharacterString(StandardCharacterEncodings.ANSI_X3_4, "hxzy-1.01"));
    }

    @Test
    public void definedScalarWithIncorrectType() {
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.description, new Real(0));
        }, ErrorClass.property, ErrorCode.invalidDataType);
    }

    @Test
    public void definedScalarWithCorrectType() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.description, new CharacterString("a"));
        Assert.assertEquals(new CharacterString("a"), d2.get(PropertyIdentifier.description));
    }

    @Test
    public void undefinedScalar() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(5557), new Real(55));
        Assert.assertEquals(new Real(55), d2.get(PropertyIdentifier.forId(5557)));

        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(5557), new CharacterString("b"));
        Assert.assertEquals(new CharacterString("b"), d2.get(PropertyIdentifier.forId(5557)));
    }

    @Test
    public void definedArrayWriteElementIncorrectType() {
        // Write a primitive type instead of a NameValue
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags, 3,
                    new BitString(new boolean[] { false }));
        }, ErrorClass.property, ErrorCode.missingRequiredParameter);
    }

    @Test
    public void definedArrayWriteElementCorrectType() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags, 3,
                new NameValue("t3", new Real(3.14F)));
        Assert.assertEquals(new BACnetArray<>( //
                new NameValue("tag1", DateTime.UNSPECIFIED), //
                new NameValue("tag2", new Real(234)), //
                new NameValue("t3", new Real(3.14F))), d2.get(PropertyIdentifier.tags));
    }

    @Test
    public void definedArrayWriteIncorrectInnerType() {
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags,
                    new SequenceOf<>(new Real(-1), new Real(0), new Real(1)));
        }, ErrorClass.property, ErrorCode.missingRequiredParameter);
    }

    @Test
    public void definedArrayWriteIncorrectType() {
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags, new Real(1));
        }, ErrorClass.property, ErrorCode.missingRequiredParameter);
    }

    @Test
    public void definedArrayWriteCorrectType() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags,
                new SequenceOf<>( //
                        new NameValue("t1", new CharacterString("v1")), //
                        new NameValue("t2", DateTime.UNSPECIFIED)));
        Assert.assertEquals(new BACnetArray<>( //
                new NameValue("t1", new CharacterString("v1")), //
                new NameValue("t2", DateTime.UNSPECIFIED)), d2.get(PropertyIdentifier.tags));
    }

    @Test
    public void undefinedArrayWrite() {
        // Fails because the property isn't parsable.
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(5678),
                    new SequenceOf<>(new Real(-0.1f), new Real(0), new Real(0.1f)));
        }, ErrorClass.property, ErrorCode.datatypeNotSupported);
    }

    @Test
    public void undefinedArrayWriteElementLowIndexDirect() {
        TestUtils.assertBACnetServiceException(() -> {
            d2.getObject(d2.getId()).writeProperty(null,
                    new PropertyValue(PropertyIdentifier.forId(6789), UnsignedInteger.ZERO, new Real(10), null));
        }, ErrorClass.property, ErrorCode.invalidArrayIndex);
    }

    @Test
    public void undefinedArrayWriteElementLowIndex() {
        // Returns invalid data type because the index of 0 indicates a write to the array length, which expects
        // an UnsignedInteger
        TestUtils.assertErrorAPDUException(() -> {
            LOG.info("Before write: {}", d2.get(PropertyIdentifier.forId(6789)));
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(6789), 0, new Real(10));
            LOG.info("After write: {}", d2.get(PropertyIdentifier.forId(6789)));
            LOG.info("After write: {}", d1.get(PropertyIdentifier.forId(6789)));
        }, ErrorClass.property, ErrorCode.invalidDataType);
    }

    @Test
    public void undefinedArrayWriteElementHighIndex() {
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(6789), 4, new Real(10));
        }, ErrorClass.property, ErrorCode.invalidArrayIndex);
    }

    @Test
    public void undefinedArrayWriteElement() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(6789), 2, new Real(10));
        Assert.assertEquals(new BACnetArray<>(new Real(0), new Real(10), new Real(2)),
                d2.get(PropertyIdentifier.forId(6789)));
    }

    @Test
    public void undefinedArrayWriteIncorrectElement() {
        TestUtils.assertErrorAPDUException(() -> {
            LOG.info("Before write: {}", d2.get(PropertyIdentifier.forId(6789)));
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(6789), 0, new SignedInteger(10));
            LOG.info("After write: {}", d2.get(PropertyIdentifier.forId(6789)));
        }, ErrorClass.property, ErrorCode.invalidDataType);
    }

    @Test
    public void definedListWriteCorrectType() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.restartNotificationRecipients,
                new SequenceOf<>( //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 10)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 11)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 12)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 13))));
        Assert.assertEquals(
                new SequenceOf<>( //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 10)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 11)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 12)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 13))),
                d2.get(PropertyIdentifier.restartNotificationRecipients));
    }

    @Test
    public void definedListWriteIncorrectType() {
        //Standard test 135.1-2013, 9.22.2.3
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.utcTimeSynchronizationRecipients, new ObjectIdentifier(ObjectType.analogOutput, 1));
        }, ErrorClass.property, ErrorCode.invalidDataType);
    }

    @Test
    public void primitiveWriteIncorrectType() {
        //Standard test 135.1-2013, 9.22.2.3
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.backupFailureTimeout, new CharacterString("This is the wrong type"));
        }, ErrorClass.property, ErrorCode.invalidDataType);
    }

    @Test
    public void primitiveReadIncorrectType() {
        //Standard test 135.1-2013, 9.22.2.3 only applies to writes
        
        try{
            //Internally this gets converted to an UnsignedInteger on receipt of the response so I'm not sure how to assert the
            //  response is correct
            RequestUtils.readProperty(d1, rd2, d2.getId(), PropertyIdentifier.protocolVersion, null);
        }catch(Exception e) {
            fail(e.getMessage());
        }
    }
    
    @Test
    public void writeOutOfRange() {
        //Standard test 135.1-2013, 9.22.2.4 - write a Unsigned16 with a value out of range.
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.backupFailureTimeout, new Unsigned32(new BigInteger("4294967295")));
        }, ErrorClass.property, ErrorCode.valueOutOfRange);
    }
}
