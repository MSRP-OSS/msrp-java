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
package javax.net.msrp.exceptions;

/** Header field cannot be parsed or has unknown/invalid content.
 * 
 * @author João André Pereira Antunes 2008
 */
@SuppressWarnings("serial")
public class InvalidHeaderException
    extends Exception
{
    public InvalidHeaderException()
    {
        /* empty */;
    }

    public InvalidHeaderException(String reason)
    {
        super(reason);
    }

    public InvalidHeaderException(Throwable cause)
    {
        super(cause);
    }

    public InvalidHeaderException(String reason, Throwable cause)
    {
        super(reason, cause);
    }
}
