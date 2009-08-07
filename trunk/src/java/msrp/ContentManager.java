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

import msrp.Stream;
import msrp.messages.Message;

import java.util.*;

/**
 * @author D
 */
public class ContentManager
{

    /**
     * @uml.property name="_message"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_contentManager:msrp.Message"
     */
    private msrp.messages.Message _message = null;

    /**
     * Getter of the property <tt>_message</tt>
     * 
     * @return Returns the _message.
     * @uml.property name="_message"
     */
    public msrp.messages.Message get_message()
    {
        return _message;
    }

    /**
		 */
    public ContentManager(String contentType, byte[] contentByteArray,
        Stream contentStream, String contentFileName)
    {
    }

    /**
			 */
    public byte[] read(int size)
    {
        return null;
    }

    /**
     * @uml.property name="reader"
     */
    private Object reader;

    /**
     * Getter of the property <tt>reader</tt>
     * 
     * @return Returns the reader.
     * @uml.property name="reader"
     */
    public Object getReader()
    {
        return reader;
    }

    /**
     * Setter of the property <tt>reader</tt>
     * 
     * @param reader The reader to set.
     * @uml.property name="reader"
     */
    public void setReader(Object reader)
    {
        this.reader = reader;
    }

    /**
     * Setter of the property <tt>_message</tt>
     * 
     * @param _message The _message to set.
     * @uml.property name="_message"
     */
    public void set_message(msrp.messages.Message _message)
    {
        this._message = _message;
    }

}
