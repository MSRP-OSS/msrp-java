/**
 * 
 */
package javax.net.msrp;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;

import javax.net.msrp.exceptions.ParseException;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * An incoming MSRP message containing an IM message composition indication in XML.
 * @author tuijldert
 */
public class IncomingStatusMessage extends IncomingMessage implements StatusMessage {
	private ImState state;
	private String composeContentType;
	private long lastActive = 0;
	private int refresh = 0;

	private static final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
	private Document isComposingXml = null;

    public IncomingStatusMessage(Session session, String messageId,
            String contentType, long size)
    {
        super(session, messageId, contentType, size, null);
    }

	/* (non-Javadoc)
	 * @see javax.net.msrp.Message#validate()
	 */
	@Override
	public void validate() throws Exception {
		try {
			byte [] content = this.getDataContainer().get(0, this.getSize()).array();

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
	}

	private static final SimpleDateFormat sdf =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	private long parseTimestamp(String timestamp) throws java.text.ParseException {
		sdf.setLenient(true);
		return sdf.parse(timestamp).getTime();
	}

	public ImState	getState() { return state; }
	public String	getComposeContentType() { return composeContentType; }
	public long		getLastActive() { return lastActive; }
	public int		getRefresh() { return refresh; }
}
