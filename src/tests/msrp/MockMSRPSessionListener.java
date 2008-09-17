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

/**
 * Class used to test the callbacks of the MSRPStack
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class MockMSRPSessionListener
    implements MSRPSessionListener
{

    /* The field results of the calls: */
    private Session acceptHookSession;

    private Message acceptHookMessage;

    private Session receiveMessageSession;

    private Message receiveMessage;

    private Session receivedReportSession;

    private Transaction receivedReportTransaction;

    private Session updateSendStatusSession;

    private Message updateSendStatusMessage;

    private long updateSendStatusNumberBytes;

    private Boolean acceptHookResult;

    /**
     * The name of the object
     */
    private String name;

    /**
     * The external to be set, or not data container object that is going to be
     * used on the acceptHook if it exists upon the calling of the trigger
     */
    private DataContainer externalDataContainer;

    static int successReportCounter = 0;

    /**
     * Constructor of the mock session listener
     * 
     * @param name the String that names this constructor, used for debug
     *            purposes
     */
    public MockMSRPSessionListener(String name)
    {
        this.name = name;
    }

    /**
     * Convenience method used for debug purposes, that prints the method and
     * the optionalStrings along with this mock name on System.out
     * 
     * @param method the method on which this function was called
     * @param optionalStrings
     */
    private void report(String method, String[] optionalStrings)
    {
        System.out.print(method + " Mock GOT CALLED!!!!!!! mock: " + this.name);
        if (optionalStrings == null)
            return;
        for (String string : optionalStrings)
        {
            System.out.print(" " + string);
        }
        System.out.println();

    }

    @Override
    public boolean acceptHook(Session session, IncomingMessage message)
    {
        while (acceptHookResult == null)
        {
            synchronized (this)
            {
                try
                {
                    report("acceptHook", null);
                    wait();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException();
                }
            }
        }
        boolean toReturn = acceptHookResult.booleanValue();

        String[] array =
        { "is going to return: " + toReturn };
        report("acceptHook", array);

        acceptHookMessage = message;
        acceptHookSession = session;
        if (externalDataContainer == null)
        {
            MemoryDataContainer dataContainer =
                new MemoryDataContainer((int)message.getSize());
            message.setDataContainer(dataContainer);
        }
        else
        {
            message.setDataContainer(externalDataContainer);
        }
        acceptHookResult = null;
        synchronized (this)
        {

            notifyAll();
            return toReturn;

        }
    }

    @Override
    public void receiveMessage(Session session, Message message)
    {
        report("receiveMessage", null);
        receiveMessage = message;
        receiveMessageSession = session;
        synchronized (this)
        {
            notifyAll();
        }
    }

    @Override
    public void receivedReport(Session session, Transaction tReport)
    {

        report("receivedReport", null);
        receivedReportSession = session;
        receivedReportTransaction = tReport;
        successReportCounter++;
        synchronized (this)
        {
            notifyAll();
        }

    }

    @Override
    public void updateSendStatus(Session session, Message message,
        long numberBytesSent)
    {
        // TODO Auto-generated method stub

    }

    /**
     * @return the acceptHookSession
     */
    public Session getAcceptHookSession()
    {
        return acceptHookSession;
    }

    /**
     * @return the acceptHookMessage
     */
    public Message getAcceptHookMessage()
    {
        return acceptHookMessage;
    }

    /**
     * @return the receiveMessageSession
     */
    public Session getReceiveMessageSession()
    {
        return receiveMessageSession;
    }

    /**
     * @return the receiveMessage
     */
    public Message getReceiveMessage()
    {
        return receiveMessage;
    }

    /**
     * @return the receivedReportSession
     */
    public Session getReceivedReportSession()
    {
        return receivedReportSession;
    }

    /**
     * @return the receivedReportTransaction
     */
    public Transaction getReceivedReportTransaction()
    {
        return receivedReportTransaction;
    }

    /**
     * @return the updateSendStatusSession
     */
    public Session getUpdateSendStatusSession()
    {
        return updateSendStatusSession;
    }

    /**
     * @return the updateSendStatusMessage
     */
    public Message getUpdateSendStatusMessage()
    {
        return updateSendStatusMessage;
    }

    /**
     * @return the updateSendStatusNumberBytes
     */
    public long getUpdateSendStatusNumberBytes()
    {
        return updateSendStatusNumberBytes;
    }

    /**
     * @param acceptHookResult the acceptHookResult to set
     */
    public void setAcceptHookResult(Boolean acceptHookResult)
    {
        this.acceptHookResult = acceptHookResult;
    }

    public void setDataContainer(DataContainer dc)
    {
        externalDataContainer = dc;
    }

}
