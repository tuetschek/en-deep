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

import en_deep.mlprocess.Process;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Scanner;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
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

    /* CONSTANTS */

    /** List of characters that are forbidden in file names (including "[]" used
     * for encoding) */
    private static final String BAD_CHARS = "[]<>|&#!:/\\*?$^@%={}`~\"'";

    /* METHODS */

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
     * Filter a data set: keep only some of the attributes. Only the attributes whose bit is
     * set to true are kept. If the
     * @param data the data to be filtered
     * @param mask attributes bit mask
     * @return the filtered data set
     */
    public static Instances filterAttributes(Instances data, BitSet mask){
    
        ArrayList<Attribute> atts = new ArrayList<Attribute> (mask.cardinality());

        for (int i = 0; i < mask.size(); ++i){
            if (mask.get(i)){
                atts.add(data.attribute(i));
            }
        }

        Instances ret = new Instances(data.relationName(), atts, data.numInstances());
        if (data.classIndex() >= 0 && mask.get(data.classIndex())){
            ret.setClass(data.classAttribute());
        }
        Enumeration<Instance> insts = data.enumerateInstances();

        while (insts.hasMoreElements()){

            Instance inst = insts.nextElement();
            double [] oldValues = inst.toDoubleArray();
            double [] newValues = new double [mask.cardinality()];
            int pos = 0;
            for (int i = 0; i < mask.size(); ++i){
                if (mask.get(i)){
                    newValues[pos++] = oldValues[i];
                }
            }

            try {
                Constructor constructor = inst.getClass().getConstructor(double.class, double[].class);
                ret.add((Instance) constructor.newInstance(inst.weight(), newValues));
            }
            catch (Exception e){
                ret.add(new DenseInstance(inst.weight(), newValues));
            }
        }

        return ret;
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

    /**
     * This reads the file contents and saves them to a string.
     * @param fileName the name of the file to be read
     * @param firstLineOnly if true, only the first line of the file will be read
     * @return the contents of the file in a string
     */
    public static String readString(String fileName, boolean firstLineOnly) throws IOException {
    
        Scanner in = new Scanner(new File(fileName), Process.getInstance().getCharset());
        StringBuilder sb = new StringBuilder();

        while (in.hasNextLine()){
            sb.append(in.nextLine());
            if (firstLineOnly){
                break;
            }
            sb.append("\n");
        }
        in.close();
        return sb.toString();
    }

    /**
     * This reads a named numeric value from a file. The file must have the <pre>name:value</tt> format.
     * If the value of the given name is not present in the file, this returns null.
     * @param fileName the name of the file to read
     * @param valueName the name of the value to look for
     * @return the desired value
     */
    public static Double readValue(String fileName, String valueName) throws IOException, NumberFormatException {

        RandomAccessFile file = new RandomAccessFile(fileName, "r");
        String line = file.readLine();
        Double val = null;

        while (line != null) {

            String[] args = line.split(":");
            args[0] = args[0].trim();
            args[1] = args[1].trim();

            if (args[0].equalsIgnoreCase(valueName)) {
                val = Double.parseDouble(args[1]);
                break;
            }
            line = file.readLine();
        }
        file.close();
        return val;
    }

    /**
     * This encodes a string so that it may be used as a file name, converting the illegal
     * characters to their Unicode HEX values.
     * @param str the string to be encoded
     * @return the encoded resulting filename-safe string
     */
    public static String fileNameEncode(String str){

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < str.length(); ++i){
            char c = str.charAt(i);
            if (c < 32 || c > 126 || BAD_CHARS.indexOf(c) != -1){
                sb.append("[").append(Integer.toHexString(c)).append("]");
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Decode a string from the encoding used by {@link #fileNameEncode(String)}.
     * @param str the string previously encoded by {@link #fileNameEncode(String)}
     * @return the original contents of the string
     */
    public static String fileNameDecode(String str){

        StringBuilder sb = new StringBuilder();
        int encCharStart, encCharEnd;

        while ((encCharStart = str.indexOf('[')) != -1 && (encCharEnd = str.indexOf(']')) != -1){

            sb.append(str.substring(0,encCharStart));
            sb.append((char) Integer.parseInt(str.substring(encCharStart+1, encCharEnd), 16));
            str = encCharEnd == str.length() -1 ? "" : str.substring(encCharEnd + 1);
        }
        sb.append(str);
        return sb.toString();
    }

}
