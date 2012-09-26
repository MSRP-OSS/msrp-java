/**
 * 
 */
package javax.net.msrp;

/**
 * IM indication of message composition.
 * @author tuijldert
 */
public interface StatusMessage {
	public ImState getState();
	public String getComposeContentType();
	public long getLastActive();
	public int getRefresh();
}
