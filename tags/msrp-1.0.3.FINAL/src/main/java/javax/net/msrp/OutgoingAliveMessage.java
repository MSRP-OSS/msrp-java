/**
 * 
 */
package javax.net.msrp;

/**
 * @author tuijldert
 *
 */
public class OutgoingAliveMessage extends OutgoingMessage
{

	public OutgoingAliveMessage(Session session)
	{
		super(session, null, null);
	}
}
