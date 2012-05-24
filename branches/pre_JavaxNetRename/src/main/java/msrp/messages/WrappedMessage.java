/**
 * 
 */
package msrp.messages;

import java.nio.ByteBuffer;

/** Interface for the wrapping and unwrapping of messages.
 * @author tuijldert
 *
 */
public interface WrappedMessage {

	/** Parse (unwrap) the buffered message.
	 * @param buffer	contains the message.
	 * @throws Exception	on parse error.
	 */
	public void parse(ByteBuffer buffer) throws Exception;

	/** Return the content-type of the wrapped message.
	 * @return	the content-type.
	 */
	public String getContentType();

	/** Return content of the specified header.
	 * @param name	name of the header
	 * @return		the value 
	 */
	public String getHeader(String name);

	/** Return content of the wrapped header
	 * @param name	name of the header
	 * @return		the value 
	 */
	public String getContentHeader(String name);

	/** Retrun content of the wrapped message.
	 * @return	the content.
	 */
	public String getMessageContent();
}
