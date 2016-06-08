/**
 * 
 */
package javax.net.msrp;

/**
 * IM indication of message composition.<br>
 * Loosely based on RFC 3994: indication of message composition for IM
 * @author tuijldert
 */
public interface StatusMessage {
	/**
	 * @return current state of the message composer (active, idle...)
	 */
	public ImState getState();

	/**
	 * @return the type of content the user is composing (text, video..)
	 */
	public String getComposeContentType();

	/**
	 * @return timestamp when last active was seen.
	 */
	public long getLastActive();

	/**
	 * @return currently used refresh interval in seconds.
	 */
	public int getRefresh();

	/**
	 * @return user that sent the indication (conferencing support)
	 */
	public String getFrom();

	/**
	 * @return user that the indication is meant for (conferencing support)
	 */
	public String getTo();
}
