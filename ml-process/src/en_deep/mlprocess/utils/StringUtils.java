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

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Process;
import en_deep.mlprocess.exception.ParamException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * given string. If the the indexes are out of range, only the actually existing part of the
     * array is joined.
     * 
     * @param data the strings to be joined
     * @param lo lower bound of the field portion (inclusive)
     * @param hi upper bound of the field portion (exclusive)
     * @param sep field separator
     * @return the concatenation of all strings using the given separator
     */
    public static String join(String [] data, int lo, int hi, String sep){

        StringBuilder sb = new StringBuilder();

        for (int i = lo; i < hi && i < data.length; ++i){
            if (i > lo){
                sb.append(sep);
            }
            sb.append(data[i]);
        }

        return sb.toString();
    }

    /**
     * Concatenates all the string representations of objects from a given field, separating them with the
     * given string.
     * @param data the objects whose string representations are to be joined
     * @param sep field separator
     * @param escape if set, it will enclose all string values in quotes and escape them; treat missing values
     *  as unquoted `?'s.
     * @return the concatenation of all the objects' string representations using the given separator
     */
    public static String join(Object[] data, String sep, boolean escape) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < data.length; ++i){
            if (i > 0){
                sb.append(sep);
            }
            if (escape && data[i] == null){
                sb.append("?");
            }
            else if(escape && !(data[i] instanceof Double)){
                sb.append("\"").append(escape(data[i].toString())).append("\"");
            }
            else {
                sb.append(data[i].toString());
            }
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
     */
    public static String escape(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Encloses all strings in double quotes and escapes them, but only if they are not numbers or contain
     * non-alphanumeric characters.
     *
     * @param str the input string
     * @return the escaped and quoted version, or the input itself, if needed
     */
    public static String protect(String str){

        if (str == null){
            return "?";
        }

        try {
            Double.parseDouble(str);
            return str;
        }
        catch (NumberFormatException e){
            if (str.matches("[a-zA-Z0-9]+")){
                return str;
            }
            return "\"" + escape(str) + "\"";
        }
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

        if (str == null || str.equals("")){
            return new int [0];
        }

        String[] divided = str.split("\\s+");
        int[] ints = new int[divided.length];

        for (int i = 0; i < divided.length; ++i) {
            ints[i] = Integer.parseInt(divided[i]);
        }
        return ints;
    }


    /**
     * This takes the parameters of a {@link en_deep.mlprocess.Task} and creates the options for the WEKA
     * classifier/filter class out of it. Boolean WEKA parameters should be set without any value in the Task
     * parameters. If there are multiple parameters with the same name needed for the WEKA class, their name
     * (character) should be suffixed with a number.
     *
     * @param parameters the {@link Task} class parameters, containing the options to be passed to WEKA
     * @return the list of all options to be passed to WEKA
     */
    public static String[] getWekaOptions(Hashtable<String, String> parameters) {

        Pair<String, String> [] paramsArray = getSortedParams(parameters, "");
        Vector<String> ret = new Vector<String>();

        // add them to an array in that order, with or without a value
        for (int i = 0; i < paramsArray.length; ++i){

            ret.add("-" + paramsArray[i].first.replaceFirst("[0-9]+$", ""));

            if (!paramsArray[i].second.equals("")){
                ret.add(paramsArray[i].second);
            }
        }

        return ret.toArray(new String [0]);        
    }

    
    /**
     * Return an array of all parameters whose names start with a given prefix and continue with a numbers,
     * sorted according to these numbers.
     *
     * @param parameters all the parameters of a task
     * @param prefix the parameters with this prefix are to be selected
     * @return the array, filled with the sorted parameter values
     */
    public static Pair<String, String> [] getSortedParams(Hashtable<String, String> parameters, String prefix){

        Vector<Pair<String, String>> classifParams = new Vector<Pair<String,String>>(parameters.size());
        Enumeration<String> allParams = parameters.keys();

        // collect all parameters, with or without values, checking the prefix
        while (allParams.hasMoreElements()) {

            String name = allParams.nextElement();
            if (prefix == null || name.startsWith(prefix)){
                String value;

                value = parameters.get(name);
                classifParams.add(new Pair(name, value));
            }
        }

        // sort the parameters of the same name by their numerical suffixes
        Pair<String, String> [] paramsArray = classifParams.toArray(new Pair[0]);
        Arrays.sort(paramsArray, new NumberSuffixFirstComparator());

        return paramsArray;
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

        String [] [] bigrams = new String [2] [];

        bigrams[0] = left;
        bigrams[1] = right;

        return nGrams(bigrams, Math.max(left.length, right.length), sep);
    }

    /**
     * Given a list of information about each member of an n-gram, this joins the
     * individual information types together for the whole n-gram, separated by the given separator.
     * Some (not all) of the members of the n-gram may be null.
     *
     * @param data an array of data for each word of an n-gram (cannot be used with "0-grams"!)
     * @param infoLen the number of the different information types for each n-gram member
     * @param sep data separator
     * @return n-grams for each input information type
     */
    public static String [] nGrams(String [] [] data, int infoLen, String sep){

        String [] results = new String [infoLen];

        if (data != null){
            for (int i = 0; i < data.length; ++i){
                if (data[i] != null){
                    for (int j = 0; j < data[i].length; ++j){
                        results[j] = (results[j] == null ? "" : results[j] + sep) + data[i][j];
                    }
                }
            }
        }

        for (int i = 0; i < results.length; ++i){
            if (results[i] == null){
                results[i] = "";
            }
        }
        return results;
    }

    /**
     * Given parameters of a {@link en_deep.mlprocess.Task} and a name prefix, this extracts the values
     * of all the parameters whose names are composed of the prefix and an ordinal number 0..count-1.
     * If name of some of the parameters doesn't continue with an ordinal number or is out of range, null
     * is returned.
     *
     * @param parameters the parameters that contain some whose names are composed of the prefix and ordinal numbers
     * @param prefix the prefix for parameter names that must be followed by ordinal numbers
     * @param count the maximum boundary for numbers in parameter names (0..count-1)
     * @return the ordered list of values for parameters with number 0..count-1 (some may be null)
     */
    public static String [] getValuesField(Hashtable<String, String> parameters, String prefix, int count){

        Enumeration<String> paramNames = parameters.keys();
        String [] field = new String [count != 0 ? count : parameters.size()];

        while (paramNames.hasMoreElements()) {

            String paramName = paramNames.nextElement();

            if (paramName.startsWith(prefix)) {
                try {
                    int paramNum = Integer.parseInt(paramName.substring(prefix.length()));
                    field[paramNum] = parameters.get(paramName);
                }
                catch (Exception e) {
                    return null;
                }
            }
        }
        if (count == 0){
            int filled = field.length-1;
            while (field[filled] == null){
                filled--;
            }
            String [] tmp = new String [filled+1];
            System.arraycopy(field, 0, tmp, 0, filled+1);
            field = tmp;
        }

        return field;
    }

    /**
     * This returns the expansion, if the given string matches the given "*"/"**"-pattern (only one variable allowed).
     * If file sub-specifications are needed, call {@link #normalizeFilePattern(java.lang.String)} first.
     * 
     * @param string the string to be tested
     * @param pattern the pattern to be matched
     * @return the expansion, if the string matches the pattern, or null
     */
    public static String matches(String string, String pattern){

        pattern = pattern.replaceFirst("(\\*+|\\$[0-9])", "*"); // ensure we have just one variable in the pattern

        String patternStart = pattern.substring(0, pattern.indexOf("*"));
        String patternEnd = pattern.endsWith("*") ? "" : pattern.substring(pattern.indexOf("*") + 1);

        if (string.startsWith(patternStart) && string.endsWith(patternEnd)
                && string.length() >= patternStart.length() + patternEnd.length()){
            return string.substring(patternStart.length(), string.length()-patternEnd.length());
        }
        return null;
    }

    /**
     * This returns the expansions for all the variables in the order they appear in the string, if the string matches
     * the given pattern that contains them.
     *
     * @param string the string to be matched
     * @param pattern a pattern with expansion variables (normalized using {@link #normalizeFilePattern(String)})
     * @return the values (expansions) of all variables contained in the pattern, or null
     */
    public static String [] matchesEx(String string, String pattern){

        String matchPat = pattern.replaceAll("([\\(\\)\\[\\]])", "\\\\$1")
                .replaceAll("\\$[0-9]", "(.*)").replaceAll("\\*+", "(.*)");
        Pattern p = Pattern.compile(matchPat);
        Matcher m = p.matcher(string);
        if (m.matches()){
            String [] vals = new String [m.groupCount()];
            for (int i = 0; i < vals.length; ++i){
                vals[i] = m.group(i+1);
            }
            return vals;
        }
        return null;
    }

    /**
     * This simplifies a filename pattern, i.e. removes all the file sub-specifications and puts them into
     * the constant part of the pattern, so that only a pattern with a single "*"/"**" or a list of variables remains.
     *
     * @param pattern the pattern to be processed
     * @return a simplified version of the pattern
     */
    public static String normalizeFilePattern(String pattern) {

        if (pattern.matches(".*\\*+(\\|[^|]+\\|).*")){ // |file| pattern - special variables & specs. possible
            return pattern.replaceFirst("\\*\\|([^|]+)\\|", "$1");
        }
        return pattern;
    }

    /**
     * This replaces the first variable of a pattern with the given replacement.
     * 
     * @param pattern the pattern to be expanded
     * @param replacement the expansion
     * @return the expanded pattern
     */
    public static String replace(String pattern, String replacement){

        pattern = normalizeFilePattern(pattern);
        return pattern.replaceFirst("(\\*+|\\$[0-9])", replacement);
    }

    /**
     * Out of an array of strings, this returns only those that match the given pattern.
     *
     * @param data the data to be filtered
     * @param pattern the pattern to be matched
     * @param negate if set to true, the function returns the strings that DON'T match the pattern
     * @return the filtered version of the original array
     */
    public static String[] getMatching(String[] data, String pattern, boolean negate) {

        ArrayList<String> matching = new ArrayList<String>(data.length);
        for (int i = 0; i < data.length; i++) {
            boolean matches = data[i].matches(pattern);
            if ((matches && !negate) || (!matches && negate)){
                matching.add(data[i]);
            }
        }
        return matching.toArray(new String [matching.size()]);
    }

    /**
     * This returns the position of a given string in the given array, or -1 if not found.
     * @param arr the array that contains the string
     * @param val the string to search
     * @return the position of the string in the array, or -1
     */
    public static int find(String [] arr, String val){

        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(val)){
                return i;
            }
        }
        return -1;
    }

    /**
     * This returns the string with the given variable replaced with the desired value. If the string
     * doesn't contain this variable, it returns null.
     * @param string the string that should contain the variable to be replaced
     * @param replacement the variable replacement
     * @param variableNo the variable number (0-9)
     * @return the string with the variable replaced, or null if the variable is not found
     */
    public static String replaceEx(String string, String replacement, int variableNo) {

        string = normalizeFilePattern(string).replaceFirst("\\*\\*", "\\$0").replaceFirst("\\*", "\\$1");
        if (string.contains("$" + variableNo)){
            return string.replace("$" + variableNo, replacement);
        }
        else {
            return null;
        }
    }

    /**
     * This will find all $X variables and return their numbers (only 0-9 are allowed). The input pattern
     * is supposed to be already {@link #normalizeFilePattern(String) normalized}.
     *
     * @param string any string to be searched for pattern variables
     * @return list of valid variable numbers (0-9) that are present in the string, or null if none such exist
     */
    public static int[] findPatternVariables(String string) {
        
        string = string.replaceFirst("\\*\\*", "\\$0").replaceFirst("\\*", "\\$1");
        int pos = 0;
        ArrayList<Integer> vars = new ArrayList<Integer>();

        while ((pos = string.indexOf('$', pos)) != -1 && pos < string.length()-1){
            if (string.charAt(pos+1) >= '0' && string.charAt(pos+1) <= '9'){
                vars.add(string.charAt(pos+1)-'0');
            }
            pos++;
        }
        if (vars.isEmpty()){
            return null;
        }

        int [] ret = new int [vars.size()];
        for (int i = 0; i < ret.length; ++i){
            ret[i] = vars.get(i);
        }
        return ret;
    }

    /**
     * This returns a pattern for creating dependencies among tasks. Here, "*" and "**" are treated
     * the same, as are all sub-specifications. If the file name doesn't contain any wildcards, it is left unchanged.
     * @param file the file whose pattern for creating dependencies is to be made
     * @return a pattern for creating dependencies
     */
    public static String getOccurencePattern(String file) {
        // treat "**" as "*" in patterns, also sub-specifications
        if (file.matches(".*\\*+(\\|[^|]+\\|)?.*") && !file.matches(".*\\*[^*]+\\*.*")) {
            file = file.replaceFirst("\\*+(\\|[^|]+\\|)?", "*");
        }
        return file;
    }


    /**
     * Split a comma-separated string that may contain quotes, ignore quoted commas and
     * heed the unquoted ones.
     * @param string the string to be split
     * @return the list of values that were separated by unquoted commas
     */
    public static Vector<String> parseCSV(String string) {

        Vector<String> list = new Vector<String>();
        boolean quoted = false;
        boolean backslash = false;
        int st = 0;

        for (int cur = 0; cur < string.length(); ++cur){

            if (string.charAt(cur) == '\\'){
                backslash = !backslash;
            }
            if (!backslash && string.charAt(cur) == '"'){
                quoted = !quoted;
            }
            else if (string.charAt(cur) == ',' && !quoted){
                list.add(string.substring(st, cur));
                st = cur + 1;
            }

            if (string.charAt(cur) != '\\'){
                backslash = false;
            }
        }
        if (st < string.length() - 1){
            list.add(string.substring(st));
        }
        return list;
    }

    public static String unquote(String string){
        string = string.substring(1, string.length()-1);
        if (string.matches("(?s).*[^\\\\]\".*")){
            return null;
        }
        return string.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Prepend the given string with the working directory path, if it is not absolute.
     * @param path the original file / path name (with or without directory specifications)
     * @return the path, with working directory prepended, if needed
     */
    public static String getPath(String path) {
        
        if (!(new File(path)).isAbsolute()){
            return Process.getInstance().getWorkDir() + path;
        }
        return path;
    }

    /**
     * This will return true, if the given string contains any pattern variables (either of expanding
     * or listing mode).
     * @param string the string to be searched
     * @param listMode true if the desired mode is listing, false for expanding mode patterns
     * @return true if the string contains the variables of the given kind
     */
    public static boolean hasPatternVariables(String string, boolean listMode) {

        string = string.replaceFirst("\\*\\*", "\\$0").replaceFirst("\\*", "\\$1");
        int pos = 0;

        while ((pos = string.indexOf('$', pos)) != -1 && pos < string.length()-1){
            if ((!listMode && string.charAt(pos+1) >= '1' && string.charAt(pos+1) <= '9')
                    || (listMode && string.charAt(pos+1) == '0')){
                return true;
            }
            pos++;
        }
        return false;
    }

    /**
     * This finds all files that match the given ("*"-only) pattern in the given list of files. They are
     * returned in a hashtable with their pattern expansions as keys.
     * 
     * @param files a list of files (paths)
     * @param pattern a file (path) pattern, using a single "*" only
     * @return the matching files, keyed under the pattern expansions
     */
    public static Hashtable<String, String> findMatchingFiles(Vector<String> files, String pattern) {

        Hashtable<String, String> ret = new Hashtable<String, String>();
        for (String file : files){
            String key = matches(file, pattern);
            if (key != null){
                ret.put(FileUtils.fileNameDecode(key), file);
            }
        }
        return ret;
    }

    /**
     * Converts a numeric value of a command-line argument parameter to its integer representation,
     * throws an exception upon error.
     *
     * @param argName  the argument name (just for exceptions)
     * @param parValue  the value of the parameter
     * @return the numeric value of the argument parameter
     * @throws ParamException if the value is not numeric
     */
    public static int getNumericArgPar(String argName, String parValue) throws ParamException {

        try {
            return Integer.parseInt(parValue);
        } catch (NumberFormatException e) {
            throw new ParamException(ParamException.ERR_NONNUMARG, argName);
        } catch (NullPointerException e) {
            throw new ParamException(ParamException.ERR_INVPAR, argName);
        }
    }    
    
    
    /* INNER CLASSES */
   
    
    /**
     * A special class for comparing the {@link #first} parts of {@link String} {@link Pair}s with respect
     * to numerical suffixes, i.e\. in the same order Windows Explorer does. This means e.g.
     * that <tt>abc12</tt> is greater than <tt>abc2</tt>.
     */
    public static class NumberSuffixFirstComparator implements Comparator<Pair<String, String>> {

        /**
         * Compare two {@link String} {@link Pair}s with respect to numerical suffixes that may occur in
         * any of the strings.
         * @param a the first object to compare
         * @param b the second object to compare
         * @return -1 / 0 / 1 according to the order of the objects
         */
        @Override
        public int compare(Pair<String, String> a, Pair<String, String> b) {

            // weird cases, null is at the beginning
            if (a.first == null){
                if (b.first == null){
                    return 0;
                }
                return -1;
            }
            else if (b.first == null){
                return 1;
            }

            // normal case
            return numberSuffixOrder(a.first, b.first);
        }

        /**
         * Compares two strings with respect to number suffixes that may occur in them.
         * @param a the first string to compare
         * @param b the second string to compare
         * @return -1 / 0 / 1 according to the order of the strings
         */
        private int numberSuffixOrder(String a, String b) {

            int numBegA = getNumPos(a);
            int numBegB = getNumPos(b);

            if (numBegA != -1 && numBegB != -1 && numBegA == numBegB
                    && a.substring(0, numBegA).equals(b.substring(0, numBegA))){

                int numA = 0;
                int numB = 0;
                try {
                    numA = Integer.parseInt(a.substring(numBegA));
                    numB = Integer.parseInt(b.substring(numBegB));
                }
                catch (NumberFormatException e){
                    Logger.getInstance().message("Number comparing error: " + e.getMessage(), Logger.V_IMPORTANT);
                }

                return numA < numB ? -1 : (numA > numB ? 1 : 0);
            }
            return a.compareTo(b);
        }

        /**
         * Find the position where a numeric suffix begins in the string.
         * @param str the string to be examined
         * @return the position where a numeric suffix begins, or -1 if there is none
         */
        private int getNumPos(String str) {

            if (str == null || str.isEmpty()){
                return -1;
            }
            if (!Character.isDigit(str.charAt(str.length()-1))){
                return -1;
            }
            int pos = str.length() - 1;
            while (pos > 0 && Character.isDigit(str.charAt(pos-1))){
                pos--;
            }
            return pos;
        }

    }


}
