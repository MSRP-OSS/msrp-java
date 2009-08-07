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

import msrp.messages.Message;

/**
 * @author D
 * 
 */
public interface ISession
{

    /**
     * @author D
     */
    /**
     * @param contentType the string with the content type of the message
     * @param fileName the string with the path of the filename to be used to
     *            fetch the content of the message
     * @return returns a reference to the newly created object Message.
     * @throws Exception Representing the error associated with the creation of
     *             the message
     */
    public Message sendMessage(String contentType, String fileName)
        throws Exception;

    /**
     * @param contentType the string with the content type of the message
     * @param byteContent the content of the message
     * @return returns a reference to the newly created object Message.
     * @throws Exception Representing the error associated with the creation of
     *             the message
     */
    public Message sendMessage(String contentType, byte[] byteContent)
        throws Exception;

    /**
     * @param contentType the string with the content type of the message
     * @param stream the stream pointing to the content of the message
     * @return returns a reference to the newly created object Message.
     * @throws Exception Representing the error associated with the creation of
     *             the message
     */
    public Message sendMessage(String contentType, Stream stream)
        throws Exception;

}
