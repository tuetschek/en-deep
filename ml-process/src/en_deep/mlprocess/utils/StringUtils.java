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

import java.util.Enumeration;
import java.util.Hashtable;
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
     * This takes the parameters of a {@link Task} and creates the options for the WEKA
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
     * @return
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

}
