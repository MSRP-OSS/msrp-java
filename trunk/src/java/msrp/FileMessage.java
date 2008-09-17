/* Copyright © João Antunes 2008
 This file is part of MSRP Java Stack.

    MSRP Java Stack is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MSRP Java Stack is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with MSRP Java Stack.  If not, see <http://www.gnu.org/licenses/>.

 */
package msrp;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * This class is used to allow different sources for the message content
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class FileMessage
    extends Message
{

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
        ReportMechanism reportMechanism) throws FileNotFoundException, SecurityException
    {
        this(session, contentType, filePath);
        constructorAssociateReport(reportMechanism);
    }

    /**
     * @param session
     * @param contentType
     * @param file
     * @throws SecurityException 
     * @throws FileNotFoundException 
     */
    public FileMessage(Session session, String contentType, File file) throws FileNotFoundException, SecurityException
    {
        super(session, contentType, file);
    }

}
