/**
 * 
 */
package javax.net.msrp;

/**
 * An outgoing MSRP message containing an IM message composition indication in XML.
 * @author tuijldert
 */
public class OutgoingStatusMessage extends OutgoingMessage implements StatusMessage {
	private ImState state;
	private String composeContentType;
	private long lastActive = 0;
	private int refresh = 0;

	private static final String ISCOMPOSING =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<isComposing>" +
			"	<state>%s</state>" +
			"	<contenttype>%s</contenttype>" +
			"	%s" +
			"</isComposing>";

	private static final String REFRESH_PARAM = "<refresh>%d</refresh>";

	private String includeRefresh(int refresh)
	{
		if (refresh > 0)
			return String.format(REFRESH_PARAM, refresh);
		else
			return "";
	}

	protected OutgoingStatusMessage(Session session, int refresh)
	{
		state = session.getImState();
		composeContentType = session.getComposeContentType();
		this.refresh = refresh;
		String content = String.format(ISCOMPOSING, state.name(),
					composeContentType, includeRefresh(refresh));

        this.session = session;
        this.contentType = Message.IMCOMPOSE_TYPE;
		messageId = Stack.generateMessageID();
        dataContainer = new MemoryDataContainer(content.getBytes());
        size = content.length();
        constructorAssociateReport(null);
        this.session.addMessageToSend(this);
	}

	public ImState	getState() { return state; }
	public String	getComposeContentType() { return composeContentType; }
	public long		getLastActive() { return lastActive; }
	public int		getRefresh() { return refresh; }
}
