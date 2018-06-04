/* Copyright © João Antunes 2008
 * This file is part of MSRP Java Stack.
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
package javax.net.msrp.exceptions;

/**
 * Generic exception for parsing MSRP stuff.
 * 
 * @author João André Pereira Antunes 2008
 */
@SuppressWarnings("serial")
public class ParseException
    extends Exception
{
    public ParseException()
    {
    }

    public ParseException(String reason)
    {
        super(reason);
    }

    public ParseException(Throwable cause)
    {
        super(cause);
    }

    public ParseException(String reason, Throwable cause)
    {
        super(reason, cause);
    }
}
