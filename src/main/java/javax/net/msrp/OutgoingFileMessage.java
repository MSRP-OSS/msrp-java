/*
 * Copyright © João Antunes 2008 This file is part of MSRP Java Stack.
 * 
 * MSRP Java Stack is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * MSRP Java Stack is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with MSRP Java Stack. If not, see <http://www.gnu.org/licenses/>.
 */
package javax.net.msrp;

import java.io.File;
import java.io.FileNotFoundException;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an outgoing MSRP message whose content is in a file.
 * 
 * @author João André Pereira Antunes
 */
public class OutgoingFileMessage
    extends OutgoingMessage
{
    /** The logger associated with this class */
    private static final Logger logger =
        LoggerFactory.getLogger(OutgoingFileMessage.class);

    protected OutgoingFileMessage(Session session, String contentType,
        File file)
        throws FileNotFoundException, SecurityException
    {
        this(session, contentType, file, null);
    }

    protected OutgoingFileMessage(Session session, String contentType,
    		File file, ReportMechanism reportMechanism)
        throws FileNotFoundException, SecurityException
    {
        this.session = session;
        this.contentType = contentType;
		messageId = Stack.generateMessageID();
        dataContainer = new FileDataContainer(file);
        size = dataContainer.size();
        constructorAssociateReport(reportMechanism);
        logger
            .trace("Outgoing file message created. Associated objects, Session: "
                + session.getURI()
                + " contentType: " + contentType
                + " File: " + file.getAbsolutePath());
        this.session.addMessageToSend(this);
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.OutgoingMessage#getSentBytes()
     */
    public long getSentBytes()
    {
        return dataContainer.currentReadOffset();
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.OutgoingMessage#isComplete()
     */
    @Override
    public boolean isComplete()
    {
        return outgoingIsComplete(getSentBytes());
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.OutgoingMessage#getDirection()
     */
    @Override
    public int getDirection()
    {
        return OUT;
    }
}
