package com.serotonin.bacnet4j.type;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;

import org.junit.Test;

import com.serotonin.bacnet4j.enums.DayOfWeek;
import com.serotonin.bacnet4j.enums.Month;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.type.constructed.CalendarEntry;
import com.serotonin.bacnet4j.type.constructed.DateRange;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.LightingCommand;
import com.serotonin.bacnet4j.type.constructed.PortPermission;
import com.serotonin.bacnet4j.type.constructed.Scale;
import com.serotonin.bacnet4j.type.constructed.TimerStateChangeValue;
import com.serotonin.bacnet4j.type.enumerated.LightingOperation;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.primitive.BitString;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.Date;
import com.serotonin.bacnet4j.type.primitive.Double;
import com.serotonin.bacnet4j.type.primitive.Enumerated;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.OctetString;
import com.serotonin.bacnet4j.type.primitive.Primitive;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.SignedInteger;
import com.serotonin.bacnet4j.type.primitive.Time;
import com.serotonin.bacnet4j.type.primitive.Unsigned8;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.type.primitive.encoding.CharacterEncoding;
import com.serotonin.bacnet4j.type.primitive.encoding.DbcsCp932CharacterEncoder;
import com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

public class EncodableTest {
    @Test
    public void _20_2_2() throws BACnetException {
        decodePrimitive("00", Null.instance);
    }

    @Test
    public void _20_2_3() throws BACnetException {
        decodePrimitive("10", Boolean.FALSE);
        decodePrimitive("2901", 2, Boolean.TRUE);
    }

    @Test
    public void _20_2_4() throws BACnetException {
        decodePrimitive("2148", new UnsignedInteger(72));
    }

    @Test
    public void _20_2_5() throws BACnetException {
        decodePrimitive("3148", new SignedInteger(72));
    }

    @Test
    public void _20_2_6() throws BACnetException {
        decodePrimitive("4442900000", new Real(72));
    }

    @Test
    public void _20_2_7() throws BACnetException {
        decodePrimitive("55084052000000000000", new Double(72));
    }

    @Test
    public void _20_2_8() throws BACnetException {
        decodePrimitive("631234FF", new OctetString(new byte[] { 0x12, 0x34, (byte) 0xFF }));
    }

    @Test
    public void _20_2_9() throws BACnetException {
        decodePrimitive("751900546869732069732061204241436E657420737472696E6721",
                new CharacterString("This is a BACnet string!"));
    }

    @Test
    public void _20_2_10() throws BACnetException {
        decodePrimitive("8203A8", new BitString(new boolean[] { true, false, true, false, true }));
    }

    @Test
    public void _20_2_11() throws BACnetException {
        decodePrimitive("9100", new Enumerated(0));
    }

    @Test
    public void _20_2_12() throws BACnetException {
        decodePrimitive("A45B011804", new Date(1991, Month.JANUARY, 24, DayOfWeek.THURSDAY));
        decodePrimitive("A45BFF18FF", new Date(1991, null, 24, null));
    }

    @Test
    public void _20_2_13() throws BACnetException {
        decodePrimitive("B411232D11", new Time(17, 35, 45, 17));
    }

    @Test
    public void _20_2_14() throws BACnetException {
        decodePrimitive("C400C0000F", new ObjectIdentifier(ObjectType.binaryInput, 15));
    }

    @Test
    public void _20_2_15() throws BACnetException {
        decodePrimitive("38", 3, Null.instance);
        decodePrimitive("6900", 6, Boolean.FALSE);
        decodePrimitive("F91B00", 27, Boolean.FALSE);
        decodePrimitive("0A0100", 0, new UnsignedInteger(256));
        decodePrimitive("59B8", 5, new SignedInteger(-72));
        decodePrimitive("F921B8", 33, new SignedInteger(-72));
        decodePrimitive("0CC2053333", 0, new Real(-33.3F));
        decodePrimitive("1D08C040A66666666666", 1, new Double(-33.3));
        decodePrimitive("FD5508C040A66666666666", 85, new Double(-33.3));
        decodePrimitive("1A4321", 1, new OctetString(new byte[] { 0x43, 0x21 }));
        decodePrimitive("5D1900546869732069732061204241436E657420737472696E6721", 5,
                new CharacterString("This is a BACnet string!"));
        decodePrimitive("FD7F1900546869732069732061204241436E657420737472696E6721", 127,
                new CharacterString("This is a BACnet string!"));
        // TODO The spec say this starts with '75'. Should probably point this out.
        decodePrimitive("7D0A004672616EC3A7616973", 7, new CharacterString("Français"));
        decodePrimitive("0A03A8", 0, new BitString(new boolean[]{true, false, true, false, true,}));
        decodePrimitive("9900", 9, new Enumerated(0));
        decodePrimitive("9C5B011804", 9, new Date(1991, Month.JANUARY, 24, DayOfWeek.THURSDAY));
        decodePrimitive("4C11232D11", 4, new Time(17, 35, 45, 17));
        decodePrimitive("4C00C0000F", 4, new ObjectIdentifier(ObjectType.binaryInput, 15));
    }

    @Test
    public void shouldDecodeJapaneseEncoding() throws BACnetException, UnsupportedEncodingException {
        ByteQueue queue = new ByteQueue();
        String text = "JapaneseEncoding ｦ";
        CharacterString value = new CharacterString(
                new CharacterEncoding(StandardCharacterEncodings.IBM_MS_DBCS,
                        DbcsCp932CharacterEncoder.JAPANESE_CODEPAGE),
                text);
        value.write(queue);

        CharacterString characterString = Encodable.read(queue, CharacterString.class);
        assertEquals(text, characterString.getValue());
    }

    private static void decodePrimitive(final String hex, final Primitive expected) throws BACnetException {
        // Decode the given hex and compare
        final ByteQueue queue = new ByteQueue(hex);
        final Primitive p = Encodable.read(queue, Primitive.class);
        assertEquals(expected, p);

        assertEquals(0, queue.size());

        // Encode the given primitive and compare the hex
        Encodable.write(queue, expected);
        assertEquals(new ByteQueue(hex), queue);
    }

    private static void decodePrimitive(final String hex, final int ctxId, final Primitive expected)
            throws BACnetException {
        // Decode the given hex and compare
        final ByteQueue queue = new ByteQueue(hex);
        final Primitive p = Encodable.read(queue, expected.getClass(), ctxId);
        assertEquals(expected, p);

        assertEquals(0, queue.size());

        // Encode the given primitive and compare the hex
        Encodable.write(queue, expected, ctxId);
        assertEquals(new ByteQueue(hex), queue);
    }

    @Test
    public void portPermission() throws BACnetException {
        final PortPermission pp = new PortPermission(new Unsigned8(14), Boolean.TRUE);
        final ByteQueue queue = new ByteQueue();
        Encodable.write(queue, pp, 9);

        assertEquals(new ByteQueue("9e090e19019f"), queue);

        final PortPermission pp2 = Encodable.read(queue, PortPermission.class, 9);
        assertEquals(14, pp2.getPortId().intValue());
        assertEquals(true, pp2.getEnabled().booleanValue());
    }

    @Test
    public void calendarEntry() throws BACnetException {
        final CalendarEntry ce = new CalendarEntry(
                new DateRange(new Date(2017, Month.JANUARY, 19, null), new Date(2017, Month.JANUARY, 23, null)));
        final ByteQueue queue = new ByteQueue();
        Encodable.write(queue, ce, 43);

        assertEquals(new ByteQueue("fe2b1ea4750113ffa4750117ff1fff2b"), queue);

        final CalendarEntry ce2 = Encodable.read(queue, CalendarEntry.class, 43);
        assertEquals(true, ce2.isDateRange());
        assertEquals(19, ce2.getDateRange().getStartDate().getDay());
        assertEquals(Month.JANUARY, ce2.getDateRange().getStartDate().getMonth());
        assertEquals(DayOfWeek.UNSPECIFIED, ce2.getDateRange().getStartDate().getDayOfWeek());
        assertEquals(117, ce2.getDateRange().getStartDate().getYear());
        assertEquals(23, ce2.getDateRange().getEndDate().getDay());
        assertEquals(Month.JANUARY, ce2.getDateRange().getEndDate().getMonth());
        assertEquals(DayOfWeek.UNSPECIFIED, ce2.getDateRange().getEndDate().getDayOfWeek());
        assertEquals(117, ce2.getDateRange().getEndDate().getYear());
    }

    @Test
    public void scale() throws BACnetException {
        final Scale scale = new Scale(new SignedInteger(-123));
        final ByteQueue queue = new ByteQueue();
        Encodable.write(queue, scale, 123);

        assertEquals(new ByteQueue("fe7b1985ff7b"), queue);

        final Scale scale2 = Encodable.read(queue, Scale.class, 123);
        assertEquals(false, scale2.isReal());
        assertEquals(-123, scale2.getSignedInteger().intValue());
    }

    @Test
    public void timerStateChangeValue() throws BACnetException {
        testTimerStateChangeValue(new TimerStateChangeValue(Null.instance), 10, "ae00af");
        testTimerStateChangeValue(new TimerStateChangeValue(Boolean.TRUE), 11, "be11bf");
        testTimerStateChangeValue(new TimerStateChangeValue(new UnsignedInteger(12)), 12, "ce210ccf");
        testTimerStateChangeValue(new TimerStateChangeValue(new SignedInteger(-23)), 13, "de31e9df");
        testTimerStateChangeValue(new TimerStateChangeValue(new Real(345.6F)), 14, "ee4443accccdef");
        testTimerStateChangeValue(new TimerStateChangeValue(new Double(456.78)), 15, "fe0f5508407c8c7ae147ae14ff0f");
        testTimerStateChangeValue(
                new TimerStateChangeValue(new OctetString(new byte[] { 1, 2, 5, 11, 56, (byte) 213 })), 16,
                "fe1065060102050b38d5ff10");
        testTimerStateChangeValue(new TimerStateChangeValue(new CharacterString("A BACnet test")), 17,
                "fe11750e0041204241436e65742074657374ff11");
        testTimerStateChangeValue(new TimerStateChangeValue(new BitString(new boolean[] { true, false, false, true })),
                18, "fe12820490ff12");
        testTimerStateChangeValue(new TimerStateChangeValue(new Enumerated(27)), 19, "fe13911bff13");
        testTimerStateChangeValue(new TimerStateChangeValue(new Date(117, Month.OCTOBER, 26, DayOfWeek.UNSPECIFIED)),
                20, "fe14a4750a1affff14");
        testTimerStateChangeValue(new TimerStateChangeValue(new Time(20, 20, 20, 20)), 40, "fe28b414141414ff28");
        testTimerStateChangeValue(new TimerStateChangeValue(new ObjectIdentifier(ObjectType.timer, 11)), 80,
                "fe50c407c0000bff50");
        testTimerStateChangeValue(new TimerStateChangeValue(), 81, "fe5108ff51");
        //        testTimerStateChangeValue(new TimerStateChangeValue(), 125, ""); ANY value
        testTimerStateChangeValue(new TimerStateChangeValue(new DateTime(new Date(2017, Month.FEBRUARY, 22, DayOfWeek.WEDNESDAY), new Time(13, 4, 20, 68))), 200,
                "fec82ea475021603b40d0414442fffc8");
        testTimerStateChangeValue(
                new TimerStateChangeValue(new LightingCommand(LightingOperation.stepUp, new Real(110), new Real(5),
                        new Real(1), new UnsignedInteger(60), new UnsignedInteger(8))),
                254, "fefe3e09031c42dc00002c40a000003c3f800000493c59083ffffe");
    }

    private static void testTimerStateChangeValue(final TimerStateChangeValue value, final int ctxId, final String hex)
            throws BACnetException {
        final ByteQueue queue = new ByteQueue();
        Encodable.write(queue, value, ctxId);

        assertEquals(new ByteQueue(hex), queue);

        final TimerStateChangeValue value2 = Encodable.read(queue, TimerStateChangeValue.class, ctxId);
        assertEquals(value, value2);
    }
}
