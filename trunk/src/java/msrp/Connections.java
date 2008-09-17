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

import msrp.exceptions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import msrp.Connection;
import msrp.utils.NetworkUtils;

/**
 * @author D
 */
public class Connections
    extends Connection
    implements Runnable
{

    public Connections(InetAddress address)
    {
        try
        {

            random = new Random();
            // activate the connection:

            /*
             * boolean localAddress = false;
             * 
             * InetAddress newAddress = InetAddress.getLocalHost();
             * 
             * // sanity check, check that the given address is a local one
             * where a // socket // could be bound InetAddress local[] =
             * InetAddress.getAllByName(InetAddress.getLocalHost()
             * .getHostName());
             * 
             * for (InetAddress inetAddress : local) { if
             * (inetAddress.equals(newAddress)) localAddress = true; } if
             * (!localAddress) throw new UnknownHostException(
             * "the given adress is not a local one");
             */

            if (NetworkUtils.isLinkLocalIPv4Address(address))
            {
                IOInterface
                    .debugln("Connections: Warning! given address is a local one: "
                        + address);
            }
            serverSocketChannel =
                SelectorProvider.provider().openServerSocketChannel();
            ServerSocket socket;

            // bind the socket to a local random port, if it's in use try again
            // FIXME could prove to be a bug if the bind procedure doesn't work
            socket = serverSocketChannel.socket();
            InetSocketAddress socketAddr;
            for (socketAddr =
                new InetSocketAddress(address,
                    random.nextInt(65535 - 1024) + 1024); !socket.isBound(); socketAddr =
                new InetSocketAddress(address,
                    random.nextInt(65535 - 1024) + 1024))
            {
                try
                {
                    socket.bind(socketAddr);
                }
                catch (IOException e)
                {

            IOInterface
                .debugln("Error! the connections hangued and didn't got a socket associated");
                    // do nothing
                }
            }

            // fill the localURI variable that contains the uri parts that are
            // associated with this connection (scheme[protocol], host and port)
            URI newLocalURI =
                new URI("msrp", null, address.getHostAddress(), socket
                    .getLocalPort(), null, null, null);
            localURI = newLocalURI;
            Thread test = new Thread(this);
            test.setName("Connections: " + localURI + "test");
            test.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private boolean hasStarted = false;

    private Thread associatedThread = null;

    private URI localURI;

    private Random random = null;

    private ServerSocketChannel serverSocketChannel = null;

    private HashMap<URI, Session> urisSessionsToIdentify =
        new HashMap<URI, Session>();

    // Protected constructor is sufficient to suppress unauthorized calls to the
    // constructor
    protected Connection Connection()
    {
        try
        {

            random = new Random();
            // activate the connection:

            boolean localAddress = false;

            InetAddress newAddress = InetAddress.getLocalHost();

            // sanity check, check that the given address is a local one where a
            // socket
            // could be bound
            InetAddress local[] =
                InetAddress.getAllByName(InetAddress.getLocalHost()
                    .getHostName());

            for (InetAddress inetAddress : local)
            {
                if (inetAddress.equals(newAddress))
                    localAddress = true;
            }
            if (!localAddress)
                throw new UnknownHostException(
                    "the given adress is not a local one");

            serverSocketChannel =
                SelectorProvider.provider().openServerSocketChannel();
            ServerSocket socket;

            // bind the socket to a local random port, if it's in use try again
            // FIXME could prove to be a bug if the bind procedure doesn't work
            socket = serverSocketChannel.socket();
            InetSocketAddress socketAddr;
            for (socketAddr =
                new InetSocketAddress(newAddress,
                    random.nextInt(65535 - 1024) + 1024); !socket.isBound(); socketAddr =
                new InetSocketAddress(newAddress,
                    random.nextInt(65535 - 1024) + 1024))
            {
                try
                {
                    socket.bind(socketAddr);
                }
                catch (IOException e)
                {

                    // do nothing
                }
            }

            // fill the localURI variable that contains the uri parts that are
            // associated with this connection (scheme[protocol], host and port)
            URI newLocalURI =
                new URI("msrp", null, newAddress.getHostAddress(), socket
                    .getLocalPort(), null, null, null);
            localURI = newLocalURI;
        }
        catch (Exception e)
        {
            IOInterface
                .debugln("Error! the connections hanged and didn't got a socket associated");
        }
        return this;
    }

    /**
     * @uml.property name="_connections"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="connections:msrp.TransactionManager"
     */
    private TransactionManager transactionManager = null;

    private HashSet<URI> existingURISessions = new HashSet<URI>();

    public TransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    protected boolean hasStarted()
    {
        return hasStarted;
    }

    public Connection connection(String relevantURI)
    {
        return null;
    }

    public void setTransactionManager(TransactionManager transactionManager)
    {
        this.transactionManager = transactionManager;
    }

    /**
     * SingletonHolder is loaded on the first execution of
     * Singleton.getInstance() or the first access to SingletonHolder.instance ,
     * not before. private static class SingletonHolder { private final static
     * Connections INSTANCE = new Connections();
     * 
     * }
     * 
     * public static Connections getInstance() { return
     * SingletonHolder.INSTANCE; }
     */
    public Thread getAssociatedThread()
    {
        return associatedThread;
    }

    @Override
    public void run()
    {
        hasStarted = true;
        associatedThread = Thread.currentThread();
        try
        {
            // Use the current serverSocketChannel to accept new connections
            while (true)
            {

                Connection connection =
                    new Connection(serverSocketChannel.accept());
                Thread newConnThread = new Thread(connection);
                newConnThread.setName("connection: " + connection.getLocalURI() + " by connections newConnThread");
                newConnThread.start();

            }
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // TODO Auto-generated method stub
        catch (URISyntaxException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    // IMPROVE it could be improved by adding the rest of the unreserved
    // characters according to rfc3986 (-._~)
    // IMPROVE can be improved the speed by not doing so much calls to the
    // Random class
    /**
     * Generates a number of random alpha-numeric and digit codes in US-ASCII
     * 
     * @param byteArray the byte array that will contain the newly generated
     *            bytes. the number of generated bytes is given by the length of
     *            the byteArray
     * 
     */

    private void generateRandom(byte[] byteArray)
    {
        int i;
        random.nextBytes(byteArray);
        for (i = 0; i < byteArray.length; i++)
        {
            if (byteArray[i] < 0)
                byteArray[i] *= -1;

            while (!((byteArray[i] >= 65 && byteArray[i] <= 90)
                || (byteArray[i] >= 97 && byteArray[i] <= 122) || (byteArray[i] <= 57 && byteArray[i] >= 48)))
            {

                if (byteArray[i] > 122)
                    byteArray[i] -= random.nextInt(byteArray[i]);
                if (byteArray[i] < 48)
                    byteArray[i] += random.nextInt(5);
                else
                    byteArray[i] += random.nextInt(10);
            }
        }
    }

    protected URI generateNewUri() throws ImplementationException, URISyntaxException 
    {

        if (localURI == null)
        {
            throw new ImplementationException(
                "There was an absurd error, the Connections doesn't"
                    + "has the needed socket info");
        }

        byte[] randomBytes = new byte[8]; // Variable used for generating the
        // random US-ASCII alpha numeric and
        // digit bytes
        // DEBUG
        IOInterface.debugln("random bytes uninitialized:"
            + (new String(randomBytes, Charset.forName("ascii"))));

        generateRandom(randomBytes);
        // DEBUG
        IOInterface.debugln("random bytes:"
            + (new String(randomBytes, Charset.forName("us-ascii")))
            + ":END of bytes ");

        // Get the current local URI.
        URI newURI =
            new URI(localURI.getScheme(), localURI.getUserInfo(), localURI
                .getHost(), localURI.getPort(), "/"
                + (new String(randomBytes, Charset.forName("us-ascii")))
                + ";tcp", localURI.getQuery(), localURI.getFragment());

        int i = 0;

        while (existingURISessions.contains(newURI))
        {
            i++;
            generateRandom(randomBytes);
            newURI =
                new URI(localURI.getScheme(), localURI.getUserInfo(), localURI
                    .getHost(), localURI.getPort(), "/"
                    + (new String(randomBytes, Charset.forName("us-ascii")))
                    + ";tcp", localURI.getQuery(), localURI.getFragment());
        }
        existingURISessions.add(newURI);

        // DEBUG
        IOInterface.debugln("generated the new URI, value of i:" + i);

        if (hasStarted() && getAssociatedThread().isAlive())
        {
            return newURI;
        }
        else
        {
            associatedThread = new Thread(this);
            associatedThread.setName("Connections: " + localURI + " associatedThread");
            associatedThread.start();
            hasStarted = true;
            return newURI;

        }

        // TODO Auto-generated method stub

    }

    protected void addUriToIdentify(URI uri, Session session)
    {
        urisSessionsToIdentify.put(uri, session);
    }

    /**
     * Returns a session from the list of sessions negotiated but yet to
     * identify
     * @param uri of the session to be identified
     * @return the associated session to the given uri, taken from the list
     * of sessions negotiated but yet to identify
     */
    public Session sessionToIdentify(URI uri)
    {
        return urisSessionsToIdentify.get(uri);
    }

    private MSRPStack instanceStack = MSRPStack.getInstance();

    protected void identifiedSession(Session session)
    {
        urisSessionsToIdentify.remove(session.getURI());
        existingURISessions.add(session.getURI());
        instanceStack.addActiveSession(session);
        // TODO disable the alarm

    }

    private ThreadGroup connectionsGroup =
        new ThreadGroup("MSRP Stack connections");

    protected void startConnectionThread(Runnable connection,
        ThreadGroup ioOperationGroup)

    {
        Thread newConnectionThread = new Thread(ioOperationGroup, connection);
        newConnectionThread.setName("Connections: " + localURI + " newConnectionThread");
        newConnectionThread.start();

    }

    /**
     * @return the connectionsGroup
     */
    protected ThreadGroup getConnectionsGroup()
    {
        return connectionsGroup;
    }

}
