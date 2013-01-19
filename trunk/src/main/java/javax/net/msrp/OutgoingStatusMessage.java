/**
 * 
 */
package javax.net.msrp;

import javax.net.msrp.utils.TextUtils;

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

	private static String includeRefresh(int refresh)
	{
		if (refresh > 0)
			return String.format(REFRESH_PARAM, refresh);
		else
			return "";
	}

	protected OutgoingStatusMessage(Session session, ImState state, String contentType, int refresh)
	{
		super(Message.IMCOMPOSE_TYPE, String.format(ISCOMPOSING, state.name(),
							contentType, includeRefresh(refresh)).getBytes(TextUtils.utf8));
		this.state = state;
		composeContentType = contentType;
		this.refresh = refresh;
	}

	@Override
	public ImState	getState() { return state; }
	@Override
	public String	getComposeContentType() { return composeContentType; }
	@Override
	public long		getLastActive() { return lastActive; }
	@Override
	public int		getRefresh() { return refresh; }
}