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
import msrp.ReportMechanism;
import msrp.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to allow different sources for the message content
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public abstract class FileMessage
   extends Message
{

    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(FileMessage.class);

    /**
     * @param session the session associated with this message
     * @param contentType the string containing the content type associated with
     *            this message content
     * @param file the File object associated with the contents of this new
     *            message. It must be writable/readable
     * @param reportMechanism the report mechanism to be used for this message
     *            or null to use the default one associated with the session
     * @throws SecurityException
     * @throws FileNotFoundException
     */
    public FileMessage(Session session, String contentType, File filePath,
        ReportMechanism reportMechanism)
        throws FileNotFoundException,
        SecurityException
    {
      
        this(session, contentType, filePath);
        constructorAssociateReport(reportMechanism);
        logger
            .trace("File Message with custom report mechanism created. Associated objects, Session: "
                + session.getURI()
                + " contentType: "
                + contentType
                + " File: "
                + filePath.getAbsolutePath());
    }

    /**
     * @param session
     * @param contentType
     * @param file
     * @throws SecurityException
     * @throws FileNotFoundException
     */
    public FileMessage(Session session, String contentType, File file)
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

}
