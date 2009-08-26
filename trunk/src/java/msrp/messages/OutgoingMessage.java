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

import msrp.Session;
import msrp.exceptions.IllegalUseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author João André Pereira Antunes
 * 
 */
public class OutgoingMessage
    extends Message
{

    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(OutgoingMessage.class);

    public OutgoingMessage(Session sendingSession, String string,
        byte[] someData) throws IllegalUseException
    {
        super(sendingSession, string, someData);
    }
    
    /**
     * Constructor used internally
     */
    protected OutgoingMessage()
    {
        
    }

    /**
     * Returns the sent bytes determined by the offset of the data container
     * @return the number of sent bytes 
     */
    public long getSentBytes()
    {
        return dataContainer.currentReadOffset();
        
    }
    /*
     * (non-Javadoc)
     * 
     * @see msrp.Message#isComplete()
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
