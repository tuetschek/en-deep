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

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < data.length; ++i){
            if (i > 0){
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
}
