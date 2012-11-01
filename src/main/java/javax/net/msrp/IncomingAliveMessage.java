/**
 * 
 */
package javax.net.msrp;

/**
 * @author tuijldert
 */
public class IncomingAliveMessage extends IncomingMessage
{

	public IncomingAliveMessage(Session session, String messageId)
	{
		super(session, messageId, null, 0, null);
	}
}
