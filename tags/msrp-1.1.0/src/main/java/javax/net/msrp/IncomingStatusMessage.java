/**
 * 
 */
package javax.net.msrp;

import java.io.*;
import java.text.SimpleDateFormat;

import javax.net.msrp.exceptions.ParseException;
import javax.net.msrp.wrap.Headers;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.*;

/**
 * An incoming MSRP message containing an IM message composition indication in XML.
 * @author tuijldert
 */
public class IncomingStatusMessage extends IncomingMessage implements StatusMessage {
	private ImState state;
	private String composeContentType;
	private long lastActive = 0;
	private int refresh = 0;
	private String from = null;
	private String to = null;

	private static final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
	private Document isComposingXml = null;

    public IncomingStatusMessage(Session session, String messageId,
            String contentType, long size)
    {
        super(session, messageId, contentType, size, null);
    }

    public IncomingStatusMessage(IncomingMessage toCopy)
    {
        super(toCopy);
        this.from = toCopy.wrappedMessage.getHeader(Headers.FROM);
        this.to = toCopy.wrappedMessage.getHeader(Headers.TO);
    }

	/* (non-Javadoc)
	 * @see javax.net.msrp.Message#validate()
	 */
	@Override
	public Message validate() throws Exception {
		try {
			byte [] content;
			if (isWrapped()) {
				content = this.wrappedMessage.getMessageContent();
			} else {
				content = this.getDataContainer().get(0, this.getSize()).array();
			}

			isComposingXml = docFactory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
		} catch (Exception e) {
			throw new ParseException("Invalid isComposing document", e);
		}
		NodeList list = isComposingXml.getElementsByTagName("state");
		if (list.getLength() == 0)
			throw new ParseException("Mandatory 'state' element missing in isComposing document");
		state = ImState.valueOf(list.item(0).getTextContent());
		list = isComposingXml.getElementsByTagName("contenttype");
		if (list.getLength() > 0)
			composeContentType = list.item(0).getTextContent();
		list = isComposingXml.getElementsByTagName("lastactive");
		if (list.getLength() > 0)
			lastActive = parseTimestamp(list.item(0).getTextContent());
		list = isComposingXml.getElementsByTagName("refresh");
		if (list.getLength() > 0)
			refresh = Integer.parseInt(list.item(0).getTextContent());

		return this;
	}

	private final SimpleDateFormat sdf =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	private long parseTimestamp(String timestamp) throws java.text.ParseException {
		sdf.setLenient(true);
		return sdf.parse(timestamp).getTime();
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
