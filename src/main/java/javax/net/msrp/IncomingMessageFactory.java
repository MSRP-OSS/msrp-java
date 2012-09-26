/**
 * 
 */
package javax.net.msrp;

/**
 * Create the correct IncomingMessage object, depending on the content-type.
 * @author tuijldert
 */
public class IncomingMessageFactory {

	public static IncomingMessage createMessage(Session session, String messageId,
        String contentType, long size)
	{
		if (contentType.equals(StatusMessage.IMCOMPOSE_TYPE))
			return new IncomingStatusMessage(session, messageId, contentType, size);
		else
			return new IncomingMessage(session, messageId, contentType, size);
	}
}
