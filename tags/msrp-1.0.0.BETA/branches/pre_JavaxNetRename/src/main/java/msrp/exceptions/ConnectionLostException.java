/**
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
package msrp.exceptions;

/**
 * @author tuijldert
 *
 */
@SuppressWarnings("serial")
public class ConnectionLostException extends Exception {

	public ConnectionLostException() {
	}

	/**
	 * @param message
	 */
	public ConnectionLostException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public ConnectionLostException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public ConnectionLostException(String message, Throwable cause) {
		super(message, cause);
	}
}
