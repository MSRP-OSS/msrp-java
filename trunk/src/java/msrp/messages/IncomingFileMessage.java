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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import msrp.FileDataContainer;
import msrp.ReportMechanism;
import msrp.Session;

/**
 * @author João André Pereira Antunes
 * 
 */
public class IncomingFileMessage
    extends IncomingMessage
{

    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(IncomingFileMessage.class);
    
    public IncomingFileMessage(Session session, String contentType, File file)
        throws FileNotFoundException,
        SecurityException
    {
        this.session = session;
        dataContainer = new FileDataContainer(file);
        size = dataContainer.size();
        messageId = session.generateMessageID();
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

    public IncomingFileMessage(Session session, String contentType, File file,
        ReportMechanism customReport)
        throws FileNotFoundException,
        SecurityException
    {
        this(session, contentType, file);
        constructorAssociateReport(reportMechanism);
        logger
            .trace("Outgoing File Message with custom report mechanism " +
                    "created. Associated objects, Session: "
                + session.getURI()
                + " contentType: "
                + contentType
                + " File: "
                + file.getAbsolutePath());
    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.messages.Message#isComplete()
     */
    @Override
    public boolean isComplete()
    {
        boolean toReturn = getCounter().isComplete();
        logger.trace("Called isComplete, received bytes: "
            + getCounter().getCount() + " message size: " + size
            + " going to return: " + toReturn);
        return toReturn;
    }

    @Override
    public int getDirection()
    {
        return IN;
    }

}
