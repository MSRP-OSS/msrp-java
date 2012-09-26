/**
 * 
 */
package javax.net.msrp;

/**
 * IM indication of message composition.
 * @author tuijldert
 */
public interface StatusMessage {
	/** content-type of an isComposing message	*/
	public static final String IMCOMPOSE_TYPE = "application/im-iscomposing+xml";

	public ImState getState();
	public String getComposeContentType();
	public long getLastActive();
	public int getRefresh();
}
