/**
 * 
 */
package javax.net.msrp;

import javax.net.msrp.utils.TextUtils;
import javax.net.msrp.wrap.Wrap;
import javax.net.msrp.wrap.cpim.Header;

/**
 * An outgoing MSRP message containing an IM message composition indication in XML.
 * @author tuijldert
 */
public class OutgoingStatusMessage extends OutgoingMessage implements StatusMessage {
	private ImState state;
	private String composeContentType;
	private long lastActive = 0;
	private int refresh = 0;
	private String from = null;
	private String to = null;

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

	protected OutgoingStatusMessage(Session session, ImState state,
			String contentType, int refresh)
	{
		super(Message.IMCOMPOSE_TYPE, String.format(ISCOMPOSING, state.name(),
							contentType, includeRefresh(refresh)).getBytes(TextUtils.utf8));
		this.state = state;
		composeContentType = contentType;
		this.refresh = refresh;
	}

	protected OutgoingStatusMessage(Session session, ImState state,
			String contentType, int refresh, String from, String to)
	{
		this(session, state, contentType, refresh);
		this.from = from;
		this.to = to;
	}

	@Override
	public Message validate() throws Exception
	{
		if (this.from != null) {
			Wrap wrap = Wrap.getInstance();
			WrappedMessage wm = wrap.getWrapper(Header.CPIM_TYPE);
			byte[] newContent = wm.wrap(from, to, contentType,
					getDataContainer().get(0, getSize()).array());
			contentType = Header.CPIM_TYPE;
			dataContainer = new MemoryDataContainer(newContent);
			size = newContent.length;
		}
		return this;
	}

	@Override
	public ImState	getState() { return state; }
	@Override
	public String	getComposeContentType() { return composeContentType; }
	@Override
	public long		getLastActive() { return lastActive; }
	@Override
	public int		getRefresh() { return refresh; }
	@Override
	public String	getFrom() { return from; }
	@Override
	public String	getTo() { return to; }
}
