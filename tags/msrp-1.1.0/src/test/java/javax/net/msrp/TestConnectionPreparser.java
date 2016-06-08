/**
 * 
 */
package javax.net.msrp;

import static org.junit.Assert.*;

import java.net.InetAddress;

import javax.net.msrp.Connection;
import javax.net.msrp.exceptions.ParseException;
import javax.net.msrp.utils.TextUtils;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author tuijldert
 *
 */
public class TestConnectionPreparser {

	Connection in;

	String Receive1InPacket =
			"MSRP G002A0C5 SEND\r\n" +
			"To-Path: javax.net.msrp://192.168.52.46:12596/jm10r107;tcp\r\n" +
			"From-Path: javax.net.msrp://192.168.51.191:1356/riTC090J;tcp\r\n" +
			"Message-ID: 1335451480454000c296b8d90\r\n" +
			"Byte-Range: 1-*/2\r\n" +
			"Content-Type: text/plain\r\n" +
			"\r\n" +
			"12" +
			"\r\n" +
			"-------G002A0C5$\r\n";

	String Receive2PerPacket =
			"MSRP G002A0C5 SEND\r\n" +
			"To-Path: javax.net.msrp://192.168.52.46:12596/jm10r107;tcp\r\n" +
			"From-Path: javax.net.msrp://192.168.51.191:1356/riTC090J;tcp\r\n" +
			"Message-ID: 1335451480454000c296b8d90\r\n" +
			"Byte-Range: 1-*/2\r\n" +
			"Content-Type: text/plain\r\n" +
			"\r\n" +
			"\r\n" +
			"\r\n" +
			"-------G002A0C5$\r\n" +
			"MSRP 22AR0e31 SEND\r\n" +
			"To-Path: javax.net.msrp://192.168.52.46:12596/jm10r107;tcp\r\n" +
			"From-Path: javax.net.msrp://192.168.51.191:1356/riTC090J;tcp\r\n" +
			"Message-ID: 1335451480454000c296b8d90\r\n" +
			"Byte-Range: 1-*/140\r\n" +
			"Content-Type: text/plain\r\n" +
			"\r\n" +
			"Hallo bij Hatseflats b.v.\r\n" +
			"Voor afdeling worst toets WORST\r\n" +
			"Voor afdeling halvezolen toets HALVEZOOL\r\n" +
			"Om de chat te beeindigen toets IKBENGEK\r\n" +
			"\r\n" +
			"-------22AR0e31$\r\n";

	@Before
	public void setUp() throws Exception {
		in = new Connection(InetAddress.getLocalHost());
	}

	@After
	public void tearDown() throws Exception {
		in.close();
	}

	@Test
	public void testPreParser1() {
		byte[] inbuffer = Receive1InPacket.getBytes(TextUtils.utf8);

		try {
			in.preParser.preParse(inbuffer, inbuffer.length);
		} catch (ParseException cpe) {
			fail("Error parsing: " + cpe.getMessage());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testPreParser2() {
		byte[] inbuffer = Receive2PerPacket.getBytes(TextUtils.utf8);

		try {
			in.preParser.preParse(inbuffer, inbuffer.length);
		} catch (ParseException cpe) {
			fail("Error parsing: " + cpe.getMessage());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
}
