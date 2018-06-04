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
package javax.net.msrp;

import static org.junit.Assert.*;

import java.io.FileOutputStream;

import org.junit.*;

/**
 * Test of sending multiple messages, with binary content, with different sizes
 * and in different ways, using all the data containers available.
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class TestSendingBinaryMessages extends TestFrame
{
    /**
     * Tests sending a 1MB Message with a MemoryDataContainer
     */
    @Test
    public void test1MbBinMsgMem2Mem()
    {
        byte[] data = new byte[1024 * 1024];
        fillBinary(data);

        byte[] receivedData = memory2Memory(data, false);

        assertArrayEquals(data, receivedData);
    }

    /**
     * Tests sending a 1MB Message with a MemoryDataContainer
     */
    @Test
    public void test1MbBinMsgMem2MemSuccessReport()
    {
        byte[] data = new byte[1024 * 1024];
        fillBinary(data);

        byte[] receivedData = memory2Memory(data, true);

        assertArrayEquals(data, receivedData);
    }

    /**
     * Tests sending a 20MB Message with a FileDataContainer to a
     * FileDataContainer
     */
    @Test
    public void test20MbBinMsgFile2File()
    {
        try
        {
            /*
             * generate random data and fill the new tempFile created before any
             * test starts:
             */
            Long startTime = System.currentTimeMillis();
            System.out.print("Start generating/writing 20MB random data...");
            byte[] data;
            FileOutputStream fileStream = new FileOutputStream(tempFile);

            for (int i = 0; i < 20; i++)
            {
                data = new byte[1024 * 1024];
                fillBinary(data);
                fileStream.write(data);
                fileStream.flush();
            }
            fileStream.close();

            System.out.println(" done generating/writing "
                + tempFile.length() + " bytes random data, took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + " s");

            /* set the random data generated to be collected by the GC */
            data = null;
            System.gc();

            file2File(false);

            assertFilesEqual();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Tests sending a 300KB Message with a FileDataContainer to a
     * FileDataContainer
     */
    @Test
    public void test300KbBinMsgFile2File()
    {
        byte[] smallData = new byte[300 * 1024];
        fillTempFile(smallData, true);
        smallData = null;
        file2File(false);
        assertFilesEqual();
    }

    /**
      * Tests sending a 300KB Message with a FileDataContainer to a
      * FileDataContainer
      */
     @Test
     public void test300KbBinMsgFile2FileSuccessReport()
     {
         byte[] smallData = new byte[300 * 1024];
         fillTempFile(smallData, true);
         smallData = null;
         file2File(true);
         assertFilesEqual();
     }

     /**
      * Tests sending a 300KB Message with a FileDataContainer
      */
     @Test
     public void test300KbBinMsgFile2Mem()
     {
         byte[] data = new byte[300 * 1024];
         fillTempFile(data, true);

         byte[] receivedData = file2Memory(false);

         assertArrayEquals(data, receivedData);
     }

     /**
      * Tests sending a 300KB Message with a FileDataContainer
      */
     @Test
     public void test300KbBinMsgFile2MemSuccessReport()
     {
         byte[] data = new byte[300 * 1024];
         fillTempFile(data, true);

         byte[] receivedData = file2Memory(true);

         assertArrayEquals(data, receivedData);
     }

     /**
      * Tests sending a 300KB Message with a MemoryDataContainer
      */
     @Test
     public void test300KbBinMsgMem2Mem()
     {
         byte[] data = new byte[300 * 1024];
         fillBinary(data);

         byte[] receivedData = memory2Memory(data, false);
         /*
          * assert that received data matches data sent
          */
         assertArrayEquals(data, receivedData);
     }

     /**
      * Tests sending a 300KB Message with a MemoryDataContainer
      */
     @Test
     public void test300KbBinMsgMem2MemSuccessReport()
     {
         byte[] data = new byte[300 * 1024];
         fillBinary(data);

         byte[] receivedData = memory2Memory(data, true);

         assertArrayEquals(data, receivedData);
     }

    /**
     * Tests sending a 5MB Message with a FileDataContainer
     */
    @Test
    public void test5MbBinMsgFile2Mem()
    {
        byte[] data = new byte[5024 * 1024];
        fillTempFile(data, true);

        byte[] receivedData = file2Memory(false);

        assertArrayEquals(data, receivedData);
    }

    /**
     * Tests sending a 5MB Message with a FileDataContainer
     */
    @Test
    public void test5MbBinMsgFile2MemSuccessReport()
    {
        byte[] data = new byte[5024 * 1024];
        fillTempFile(data, true);

        byte[] receivedData = file2Memory(true);

        assertArrayEquals(data, receivedData);
    }

    /**
     * Tests sending a 5MB Message with a MemoryDataContainer
     */
    @Test
    public void test5MbBinMsgMem2Mem()
    {
        byte[] data = new byte[5024 * 1024];
        fillBinary(data);

        byte[] receivedData = memory2Memory(data, false);

        assertArrayEquals(data, receivedData);
    }

    /**
     * Tests sending a 5MB Message with a MemoryDataContainer
     */
    @Test
    public void test5MbBinMsgMem2MemSuccessReport()
    {
        byte[] data = new byte[5024 * 1024];
        fillBinary(data);

        byte[] receivedData = memory2Memory(data, true);

        assertArrayEquals(data, receivedData);
    }
}