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
 * Default report mechanism offered by this stack as a singleton
 * 
 * TODO change the shouldGenerateReport method
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class DefaultReportMechanism
    extends ReportMechanism
{

    /**
     * Constructor for the singleton class default report mechanism defined to
     * protect unauthorized instances of this class
     */
    protected DefaultReportMechanism()
    {
    }

    /**
     * SingletonHolder is loaded on the first execution of
     * Singleton.getInstance() or the first access to SingletonHolder.instance ,
     * not before.
     */
    private static class SingletonHolder
    {
        private final static DefaultReportMechanism INSTANCE =
            new DefaultReportMechanism();

    }

    public static DefaultReportMechanism getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.ReportMechanism#shouldGenerateReport(msrp.Message, int)
     */
    /**
     * This method is called every time getTriggerGranularity() of the message is received
     * 
     * The default success report granularity is the whole message
     * @see #getTriggerGranularity()
     */
    @Override
    public boolean shouldGenerateReport(Message message,
        long lastCallCount, long CallCount)
    {
        if (message.isComplete())
            return true;
        return false;
    }

    /**
     * if the message size is unknown dont trigger
     * 
     * The default sent hook granularity is for each 10% of the message if the
     * message is bigger than 500K
     * 
     * else only trigger once when it passes the 49% to 50% barrier
     * 
     * also if the message is complete trigger it
     * 
     */
    @Override
    public boolean shouldTriggerSentHook(Message outgoingMessage,
        Session session, long nrBytesLastCall)
    {
    	if (outgoingMessage.isComplete())
    		return true;
        if (outgoingMessage.getSize() == Message.UNKNWON)
            return false;
        else if (outgoingMessage.getSize() > Message.UNINTIALIZED)
        {
            int lastPercentage =
                (int) nrBytesLastCall * 100 / (int)outgoingMessage.getSize();
            int currentPercentage =
                (int) outgoingMessage.getDataContainer().currentReadOffset()
                    * 100 / (int)outgoingMessage.getSize();
            if (outgoingMessage.size <= 500 * 1024)
            {
                if (lastPercentage < 50 && currentPercentage >= 50)
                    return true;
            }
            else
            {
                if (lastPercentage / 10 == (currentPercentage / 10) - 1)
                    return true;
                return false;

            }
        }
        return false;
    }

    @Override
    public int getTriggerGranularity()
    {
        return 1024;
    }

}
