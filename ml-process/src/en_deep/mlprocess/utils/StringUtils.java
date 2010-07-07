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

import java.io.File;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

/**
 * This class comprises several useful string functions, which do not pertain to a specific object type.
 *
 * @author Ondrej Dusek
 */
public class StringUtils {

    /**
     * Concatenates all the strings from a given field, separating them with the
     * given string.
     * @param data the strings to be joined
     * @param sep field separator
     * @return the concatenation of all strings using the given separator
     */
    public static String join(String [] data, String sep){
        return StringUtils.join(data, 0, data.length, sep);
    }


    /**
     * Concatenates all the strings from a given field portion, separating them with the
     * given string.
     * @param data the strings to be joined
     * @param lo lower bound of the field portion (inclusive)
     * @param hi upper bound of the field portion (exclusive)
     * @param sep field separator
     * @return the concatenation of all strings using the given separator
     */
    public static String join(String [] data, int lo, int hi, String sep){

        StringBuilder sb = new StringBuilder();

        for (int i = lo; i < hi; ++i){
            if (i > lo){
                sb.append(sep);
            }
            sb.append(data[i]);
        }

        return sb.toString();
    }

    /**
     * This concatenates all the objects from the given collection, separating them
     * with the given string. The {@link Object#toString()} method is called for each element.
     * @param data the object whose string representations are to be joined
     * @param sep field separator
     * @return the concatenation of all string representations using the given separator
     */
    public static String join(Collection data, String sep){

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        Iterator members = (Iterator) data.iterator();
        while (members.hasNext()){
            if (!first){
                sb.append(sep);
            }
            else {
                first = false;
            }
            sb.append(members.next().toString());
        }
        return sb.toString();

    }

    /**
     * Escapes a string to be used in quotes in an ARFF file.
     *
     * @param str the input string
     * @return the escaped version
     * @todo move to {@link Feature}
     */
    public static String escape(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * This returns the numeric value of a variable that is contained somewhere in the string.
     * The value is recognized as a number following the variable name and a colon.
     * @param str The string which is searched
     * @param var The name of the desired variable
     * @return The value of the given variable
     * @throws NumberFormatException if the value is not found
     */
    public static int findVariableVal(String str, String var) throws NumberFormatException {

        int pos = str.indexOf(var + ":");
        int end;
        if (pos == -1){
            throw new NumberFormatException(var + " not found");
        }
        pos += var.length() + 1;
        end = str.indexOf(" ", pos);
        if (end == -1){
            end = str.length();
        }
        return Integer.parseInt(str.substring(pos, end));
    }

    /**
     * This reads a space-separated list of integers from a string.
     * @param str the string from which the input is read
     * @return the list of integers that were in the string
     * @throws NumberFormatException if the string doesn't contain only space-separated integers
     */
    public static int[] readListOfInts(String str) throws NumberFormatException {

        String[] divided = str.split("\\s+");
        int[] ints = new int[divided.length];

        for (int i = 0; i < divided.length; ++i) {
            ints[i] = Integer.parseInt(divided[i]);
        }
        return ints;
    }


    /**
     * This takes the parameters of a {@link en_deep.mlprocess.Task} and creates the options for the WEKA
     * classifier/filter class out of it. Boolean WEKA parameters should
     * be set without any value in the Task parameters.
     *
     * @return the list of all options to be passed to WEKA
     */
    public static String[] getWekaOptions(Hashtable<String, String> parameters) {

        Vector classifParams = new Vector<String>(parameters.size());
        Enumeration<String> allParams = parameters.keys();

        while (allParams.hasMoreElements()) {

            String name = allParams.nextElement();
            String value;

            value = parameters.get(name);

            if (value.equals("")) { // boolean parameters should have no value
                classifParams.add("-" + name);
            }
            else {
                classifParams.add("-" + name);
                classifParams.add(value);
            }
        }
        return (String[]) classifParams.toArray(new String[0]);
    }

    /**
     * This retrieves all the parameters that start with a given prefix from a parameter set. It strips the
     * prefix off the parameter names and leave their values unchanged.
     * 
     * @param origParams the original parameters
     * @param prefix the prefix
     * @return all the parameters that start with the given prefix, with that prefix stripped off
     */
    public static Hashtable<String, String> getPrefixParams(Hashtable<String, String> origParams, String prefix){

        Hashtable<String, String> prefixParams = new Hashtable<String, String>();

        for (String key : origParams.keySet()){
            if (key.startsWith(prefix)){
                prefixParams.put(key.substring(prefix.length()),origParams.get(key));
            }
        }
        return prefixParams;
    }

    /**
     * Return specified substrings for all the members of the given array. If the strings are not long enough,
     * returns the longest possible part.
     *
     * @param arr the array of strings to make the substrings of
     * @param start the starting index of the substring (incl.)
     * @param end the ending index of the substring (excl.)
     * @return substrings of the specified range for all the strings in the given array
     */
    public static String[] substrings(String[] arr, int start, int end) {

        String [] subs = new String [arr.length];

        for (int i = 0; i < arr.length; ++i){
            subs[i] = safeSubstr(arr[i], start, end);
        }
        return subs;
    }

    /**
     * This returns a substring of the given string even if the string is not long enough (the substring will be truncated then).
     * @param str the string to take the substring of
     * @param start the starting index
     * @param end the ending index
     */
    public static String safeSubstr(String str, int start, int end) {
        if (str.length() >= end){
            return str.substring(start, end);
        }
        else if (str.length() >= start){
            return str.substring(start);
        }
        else {
            return "";
        }
    }

    /**
     * This extracts the last part of a file path -- a file name -- from a string. If the string does not
     * contain any path separator characters, it is returned as a whole.
     * @param fileName the path to be truncated to the file name
     * @return the last part of the file path -- a file name
     */
    public static String truncateFileName(String fileName) {
        if (fileName.contains(File.separator)){
            fileName = fileName.substring(fileName.lastIndexOf(File.separator) + 1);
        }
        return fileName;
    }

    /**
     * This converts a list of integers to strings and joins them with the given separator
     * @param data the list of integers to be joined
     * @param sep the field separator
     * @return the joined list of integers
     */
    public static String join(int[] data, String sep) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(Integer.toString(data[i]));
        }
        return sb.toString();
    }

    /**
     * This joins the members of the given arrays to an array of bigrams, using the given
     * separator. It is assumed that the arrays are of the same length.
     *
     * @param left the left parts of the bigrams
     * @param right the right parts of the bigrams
     * @param sep the bigram separator
     * @return the members of both arrays, joined with the given separator
     */
    public static String [] bigrams(String [] left, String [] right, String sep){

        String [] results = new String [left.length];

        for (int i = 0; i < left.length; i++) {
            results[i] = left[i] + sep + right[i];
        }
        return results;
    }

}
