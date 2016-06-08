/**
 * 
 */
package javax.net.msrp.wrap.cpim;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import javax.net.msrp.WrappedMessage;
import javax.net.msrp.utils.TextUtils;
import javax.net.msrp.wrap.Wrap;

import org.junit.Test;

/**
 * @author tuijldert
 *
 */
public class TestWrappedMessage {
	private static String cpimMessage =
			"From: MR SANDERS <im:piglet@100akerwood.com>\r\n" +
		    "To: Depressed Donkey <im:eeyore@100akerwood.com>\r\n" +
		    "DateTime: 2000-12-13T13:40:00-08:00\r\n" +
		    "Subject: the weather will be fine today\r\n" +
		    "\r\n" +
		    "Content-type: text/plain\r\n" +
		    "Content-ID: <1234567890@foo.com>\r\n" +
		    "\r\n" +
		    "Here is the text of my message.";

	private ByteBuffer buffer = ByteBuffer.wrap(cpimMessage.getBytes(TextUtils.utf8), 0, cpimMessage.length());

	/**
	 * Test method for {@link javax.net.msrp.wrap.cpim.Message#parse(java.nio.ByteBuffer)}.
	 */
	@Test
	public void testParse() {
		WrappedMessage msg = Wrap.getInstance().getWrapper("message/cpim");
		try {
			msg.parse(buffer);
		} catch (Exception e) {
			fail(e.getMessage());
		}
		assertNotNull(msg.getContentType());
		assertNotNull(msg.getHeader("From"));
		assertNotNull(msg.getContentHeader("content-id"));
		assertNotNull(msg.getMessageContent());
	}

	@Test
	public void testWrap() {
		WrappedMessage msg = Wrap.getInstance().getWrapper("message/cpim");

		msg.wrap("from", "to", "text/plain", "hello world".getBytes(TextUtils.utf8));

		String test = msg.toString();
		assertTrue(test.contains("hello"));
		assertEquals(msg.getHeader("From"), "from");
	}
}
