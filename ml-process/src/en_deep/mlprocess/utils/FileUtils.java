/*
 *  Copyright (c) 2010 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess.utils;

import java.io.*;
import java.nio.channels.*;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;

/**
 * A class that unites some basic file manipulation functions.
 *
 * @author Ondrej Dusek
 */
public class FileUtils{

    /**
     * This copies the given file to the given location (both names must be valid).
     * It just casts the Strings to {@link File}s and calls {@link #copyFile(String, String)}.
     *
     * @param source the source file
     * @param destination the destination file
     * @throws IOException in case an I/O error occurs
     */
    public static void copyFile(String source, String destination) throws IOException {
        copyFile(new File(source), new File(destination));
    }

    /**
     * This copies the given file to the given location (both names must be valid).
     * (modified after <a href="http://www.rgagnon.com/javadetails/java-0064.html">this. webpage</a>.)
     *
     * @param source the source file
     * @param destination the destination file
     * @throws IOException in case an I/O error occurs
     */
    public static void copyFile(File source, File destination) throws IOException {

        FileChannel inChannel = new FileInputStream(source).getChannel();
        FileChannel outChannel = new FileOutputStream(destination).getChannel();

        try {
           int maxCount = (64 * 1024 * 1024) - (32 * 1024);
           long size = inChannel.size();
           long position = 0;
           while (position < size) {
              position += inChannel.transferTo(position, maxCount, outChannel);
           }
        }
        catch (IOException e) {
            throw e;
        }
        finally {
            if (inChannel != null) {
                inChannel.close();
            }
            if (outChannel != null) {
                outChannel.close();
            }
        }
    }

    /**
     * This reads the contents of an ARFF (or convertible) data file, using WEKA code.
     *
     * @param fileName the name of the file to read
     * @param close force close the file after reading ?
     * @return the file contents
     * @throws Exception if an I/O error occurs
     */
    public static Instances readArff(String fileName, boolean close) throws Exception {

        FileInputStream in = new FileInputStream(fileName);
        ConverterUtils.DataSource reader = new ConverterUtils.DataSource(in);
        Instances data = reader.getDataSet();

        if (close){
            in.getChannel().force(true);
            in.getFD().sync();
        }
        in.close();
        in = null;

        return data;
    }

    /**
     * This reads the contents of an ARFF (or convertible) data file, using WEKA code.
     *
     * @param fileName the name of the file to read
     * @return the file contents
     * @throws Exception if an I/O error occurs
     */
    public static Instances readArff(String fileName) throws Exception {
        return readArff(fileName, false);
    }

    /**
     * This reads just the internal structure of a given ARFF file.
     *
     * @param fileName the name of the file to read
     * @return the file structure
     * @throws Exception if an I/O error occurs
     */
    public static Instances readArffStructure(String fileName) throws Exception {
        return readArffStructure(fileName, false);
    }

    /**
     * This reads the internal structure of a given ARFF file.
     * @param fileName the name of the file to read
     * @param close force close the file after reading ?
     * @return the file structure
     * @throws Exception if an I/O error occurs
     */
    public static Instances readArffStructure(String fileName, boolean close) throws Exception {

        FileInputStream in = new FileInputStream(fileName);
        ConverterUtils.DataSource reader = new ConverterUtils.DataSource(in);
        Instances data = reader.getStructure();

        if (close){
            in.getChannel().force(true);
            in.getFD().sync();
        }
        in.close();
        in = null;

        return data;
    }


    /**
     * This writes the given data into an ARFF file using WEKA code and closes the file
     * afterwards.
     *
     * @param fileName the file to write into
     * @param data the data to be written
     * @throws Exception if an I/O error occurs
     */
    public static void writeArff(String fileName, Instances data) throws Exception {

        FileOutputStream os = new FileOutputStream(fileName);
        ConverterUtils.DataSink writer = new ConverterUtils.DataSink(os);

        writer.write(data);
        os.close();
    }

    /**
     * This writes a string into a given file. It opens the file, rewrites everything in it and
     * closes it afterwards.
     *
     * @param fileName the file to write into
     * @param str the string to be written
     * @throws IOException if an I/O error occurs
     */
    public static void writeString(String fileName, String str) throws IOException {

        FileOutputStream os = new FileOutputStream(fileName);

        os.write(str.getBytes());
        os.close();
    }


    /**
     * This deletes the specified file. If the file is still open, it won't be deleted and false
     * is returned.
     * @param fileName the file name
     * @return true if the file was really deleted, false otherwise
     * @throws SecurityException if the file is not accessible
     */
    public static boolean deleteFile(String fileName) throws SecurityException {

        File file = new File(fileName);

        return file.delete();
    }

    /**
     * This converts all the string attributes in the data set to nominal attributes.
     * @param data the data to be processed
     * @return the data, with string attributes converted to nominal
     * @throws Exception
     */
    public static Instances allStringToNominal(Instances data) throws Exception {

        StringToNominal filter = new StringToNominal();
        StringBuilder toConvert = new StringBuilder();
        String oldName = data.relationName();

        // get the list of attributes to be converted
        for (int i = 0; i < data.numAttributes(); ++i) {
            if (data.attribute(i).isString()) {
                if (toConvert.length() != 0) {
                    toConvert.append(",");
                }
                toConvert.append(Integer.toString(i + 1));
            }
        }

        // convert the strings to nominal
        filter.setAttributeRange(toConvert.toString());
        filter.setInputFormat(data);
        data = Filter.useFilter(data, filter);
        data.setRelationName(oldName);
        
        return data;
    }

}
