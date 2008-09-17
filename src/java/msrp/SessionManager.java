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

import msrp.Session;

/**
 * @author D
 */
public class SessionManager
{

    /**
     * @uml.property name="_sessions"
     * @uml.associationEnd inverse="_sessionManager:msrp.Session"
     */
    private msrp.Session _sessions;

    private static SessionManager _sessionManager;

    /**
     * Getter of the property <tt>_sessions</tt>
     * 
     * @return Returns the _sessions.
     * @uml.property name="_sessions"
     */
    public msrp.Session get_sessions()
    {
        return _sessions;
    }

    /**
     * Setter of the property <tt>_sessions</tt>
     * 
     * @param _sessions The _sessions to set.
     * @uml.property name="_sessions"
     */
    public void set_sessions(msrp.Session _sessions)
    {
        this._sessions = _sessions;
    }

    /**
		 */
    public SessionManager()
    {
        if (_sessionManager == this)
            return;
        _sessionManager = this;
        return;
    }

    /**
			 */
    public void addSession(Session session)
    {
    }

    /**
				 */
    public void delSession(Session session)
    {
    }

}
