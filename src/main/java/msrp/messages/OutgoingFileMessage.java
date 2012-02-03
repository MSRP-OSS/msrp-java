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
package msrp.messages;

import java.io.File;
import java.io.FileNotFoundException;

import msrp.FileDataContainer;
import msrp.MSRPStack;
import msrp.ReportMechanism;
import msrp.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that represents an outgoing MSRP message whose content is in a file
 * 
 * @author João André Pereira Antunes
 * 
 */
public class OutgoingFileMessage
    extends OutgoingMessage
{

    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(OutgoingFileMessage.class);

    public OutgoingFileMessage(Session session, String contentType,
        File filePath, ReportMechanism reportMechanism)
        throws FileNotFoundException,
        SecurityException
    {
        this(session, contentType, filePath);
        constructorAssociateReport(reportMechanism);
        logger.trace("Outgoing File Message with custom report mechanism "
            + "created. Associated objects, Session: " + session.getURI()
            + " contentType: " + contentType + " File: "
            + filePath.getAbsolutePath());
    }

    public OutgoingFileMessage(Session session, String contentType, File file)
        throws FileNotFoundException,
        SecurityException
    {
        this.session = session;
        dataContainer = new FileDataContainer(file);
        size = dataContainer.size();
		messageId = MSRPStack.getInstance().generateMessageID(session);
        this.session.addMessageToSend(this);
        constructorAssociateReport(reportMechanism);
        this.contentType = contentType;
        logger
            .trace("File Message with normal (default) report mechanism created. Associated objects, Session: "
                + session.getURI()
                + " contentType: "
                + contentType
                + " File: "
                + file.getAbsolutePath());
    }

    /**
     * Returns the sent bytes determined by the offset of the data container
     * 
     * @return the number of sent bytes
     */
    public long getSentBytes()
    {
        return dataContainer.currentReadOffset();

    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.messages.Message#isComplete()
     */
    @Override
    public boolean isComplete()
    {
        return outgoingIsComplete(getSentBytes());
    }

    @Override
    public int getDirection()
    {
        return OUT;
    }

}
