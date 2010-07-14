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

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Instances;

/**
 * This class sorts the inputs according to a given condition.
 * @author Ondrej Dusek
 */
public class ConditionalSelector extends GroupInputsTask {

    /* CONSTANTS */

    /** The "condition" parameter name */
    private static final String CONDITION = "condition";
    /** The "attribute" parameter name */
    private static final String ATTRIBUTE = "attribute";
    /** The "boundaries" parameter name */
    private static final String BOUNDARIES = "boundaries";

    private enum Condition {

        UNARY, DIVIDE_BY_NUM_VAL, ORPHANS, NUM_INSTANCES;

        /**
         * This constructs the condition from a string value.
         *
         * @param str the text from which the {@link Condition} should be created
         * @return the {@link Condition} with the desired value, or null
         */
        static Condition getFromString(String str){

            if (str == null){
                return null;
            }
            else if (str.equalsIgnoreCase("unary")){
                return UNARY;
            }
            else if (str.equalsIgnoreCase("divide_by_num_val")){
                return DIVIDE_BY_NUM_VAL;
            }
            else if (str.equalsIgnoreCase("orphans")){
                return ORPHANS;
            }
            else if (str.equalsIgnoreCase("num_instances")){
                return NUM_INSTANCES;
            }
            else {
                return null;
            }
        }
    }

    /* DATA */

    /** The condition for the selection */
    private final Condition condition;
    /** If {@link #condition} is {@link Condition#UNARY} or {@link Condition#DIVIDE_BY_NUM_VAL}, this
     * stores the name of the attribute to be checked. */
    private String attrName;
    /** If {@link #condition} is {@link Condition#DIVIDE_BY_NUM_VAL}, this stores the boundaries of
     * division.     */
    private int [] boundaries;
    /** The tables that contain all the files matching the different input patterns sorted unter the same expansion keys */
    private Hashtable<String, String> [] tables;

    /* METHODS */


    /**
     * This just checks the parameters and creates a new instance. There is one compulsory parameter:
     * <ul>
     * <li><tt>condition</tt> -- may currently have following values:
     * <ul>
     * <li>"unary" -- check unarity, outputs are first unary and then non-unary</li>
     * <li>"divide_by_num_val" -- divide by numbers of values of the given attribute and the given boundaries</li>
     * <li>"orphan" -- if there are files which don't match correspondingly in the tables (only some patterns
     * correspond to a given expansion), they go into the first output. The correctly matching files go into the second
     * output</li>
     * </ul></li>
     * </ul>
     * Other parameters depend on the setting of condition:
     * <ul>
     * <li><tt>attribute</tt> -- if condition is "unary" or "divide_by_num_val", this gives the name of the attribute which
     * should be checked</li>
     * <li><tt>boundaries</tt> -- if condition is "divide_by_num_val", this gives the boundary values of division,
     * sorted and divided by spaces (the boundary values will be included in the higher interval)
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    public ConditionalSelector(String id, Hashtable<String, String> parameters, Vector<String> input,
            Vector<String> output) throws TaskException {
        super(id, parameters, input, output);

        // get the condition
        this.condition = Condition.getFromString(this.parameters.get(CONDITION));
        if (this.condition == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Condition missing.");
        }

        // initialize according to the condition
        switch (this.condition){
            case UNARY:

                if (!this.hasParameter(ATTRIBUTE)){
                    throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Attribute missing.");
                }
                this.attrName = this.parameters.get(ATTRIBUTE);
                this.extractPatterns(2);
                break;

            case DIVIDE_BY_NUM_VAL:

                if (!this.hasParameter(ATTRIBUTE) || !this.hasParameter(BOUNDARIES)){
                    throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Some parameters missing.");
                }
                this.attrName = this.getParameterVal(ATTRIBUTE);
                try {
                    this.boundaries = StringUtils.readListOfInts(this.getParameterVal(BOUNDARIES));
                }
                catch (NumberFormatException e){
                    throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Boundaries must be numeric.");
                }
                this.extractPatterns(this.boundaries.length+1);
                break;

            case NUM_INSTANCES:
                if (!this.hasParameter(BOUNDARIES)){
                    throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Boundaries missing.");
                }
                try {
                    this.boundaries = StringUtils.readListOfInts(this.getParameterVal(BOUNDARIES));
                }
                catch (NumberFormatException e){
                    throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Boundaries must be numeric.");
                }
                this.extractPatterns(this.boundaries.length+1);
                break;

            case ORPHANS:
                this.extractPatterns(2);
                break;
        }
    }

    @Override
    public void perform() throws TaskException {

        this.tables = this.sortInputs();
        Vector<String> allKeys = this.selectViableExpansions(tables);

        try {
            // process the expansions that are represented in all hashtables
            for (String key : allKeys){

                switch (this.condition){

                    case UNARY:
                        this.copyToTarget(key, this.hasUnaryAttribute(key) ? 0 : 1);
                        break;

                    case DIVIDE_BY_NUM_VAL:
                        this.copyToTarget(key, this.divideByNumberOfAttrValues(key));
                        break;

                    case NUM_INSTANCES:
                        this.copyToTarget(key, this.divideByNumberOfInstances(key));
                        break;

                    case ORPHANS:
                        this.copyToTarget(key, 1);
                        break;
                }
            }
            // process the orphans (only for Condition.ORPHANS)
            if (this.condition == Condition.ORPHANS){
                this.copyOrphans(allKeys, 0);
            }
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e){
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

    /**
     * This copies all the source files that are valid for the given key into the destination
     * of the given number.
     *
     * @param key the expansion value for the file names in the different tables
     * @param destNo the number of the destination for the files
     */
    private void copyToTarget(String key, int destNo) throws IOException {

        for (int i = 0; i < this.tables.length; ++i){

            String dest = this.output.get(destNo * this.tables.length + i).replace("**", this.outPrefix + key);
            String src = this.tables[i].get(key);

            FileUtils.copyFile(src, dest);
        }
    }


    /**
     * This tests whether all the files stored under the given expansion key in the filename tables
     * have the same attributes with the same possible values and the given one is unary.
     *
     * @param key the expansion key to look up the file names in the {@link #tables}
     * @return true if the given attribute is unary for all given files
     * @throws TaskException if the files don't have the same attributes or the desired one is missing
     */
    private boolean hasUnaryAttribute(String key) throws Exception {

        Instances[] data = this.readAndCheckHeaders(key);
        
        if (data[0].attribute(this.attrName) == null || data[0].attribute(this.attrName).numValues() == 0){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute " + this.attrName
                    + " not found or not numeric for " + key + "-like files.");
        }
        return data[0].attribute(this.attrName).numValues() == 1;
    }


    /**
     * This tests how many values does the file have for the given attribute and returns the corresponding
     * number of cluster (according to {@link #boundaries}).
     *
     * @param key the expansion key to look up the file names in the {@link #tables}
     * @return the number of division group, according to the {@link #boundaries}
     * @throws TaskException if the files don't have the same attributes or the desired one is missing
     */
    private int divideByNumberOfAttrValues(String key) throws Exception {

        Instances [] data = this.readAndCheckHeaders(key);

        if (data[0].attribute(this.attrName) == null || data[0].attribute(this.attrName).numValues() == 0){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute " + this.attrName
                    + " not found or not nominal for " + key + "-like files.");
        }

        int i = 0;
        while (i < this.boundaries.length && data[0].attribute(this.attrName).numValues() > this.boundaries[i]){
            i++;
        }
        return i;
    }

    /**
     * This divides the data according to the number of instances (the total sum of all the data
     * is used) and the {@link #boundaries}.
     * 
     * @param key the expansion key to look up the file names in the {@link #tables}
     * @return the number of division group, according to the {@link #boundaries}
     */
    private int divideByNumberOfInstances(String key) throws Exception {

        Instances [] data = this.readAndCheckHeaders(key);
        int sum = 0;
        for (int i = 0; i < data.length; ++i){
            sum += data[i].numInstances();
        }

        int i = 0;
        while (i < this.boundaries.length && sum > this.boundaries[i]){
            i++;
        }
        return i;

    }


    /**
     * Reads the headers of all files in the {@link #tables} that correspond to the given key and checks if
     * their headers are equal. Returns all the headers.
     * @param key the expansion key
     * @return the headers of all files under the given expansion key
     * @throws Exception if an I/O error occurs or if the files don't have the same headers
     */
    private Instances[] readAndCheckHeaders(String key) throws Exception {

        Instances[] data = new Instances[this.tables.length];

        for (int i = 0; i < this.tables.length; ++i) {
            data[i] = FileUtils.readArffStructure(this.tables[i].get(key));
            if (i > 0 && !data[i].equalHeaders(data[0])) {
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                        "Files with the same expansion " + key + "don't have the same headers.");
            }
        }
        return data;
    }

    /**
     * This copies all the orphan input files to the given destination (files that come under the expansions for which 
     * only some patterns apply).
     *
     * @param list of all non-orphan expansions
     * @param dest the destination file pattern
     */
    private void copyOrphans(Vector<String> nonOrphans, int destNo) throws IOException {

        for (int i = 0; i < this.tables.length; i++) {
            for (String key : this.tables[i].keySet()){
                if (!nonOrphans.contains(key)){
                    FileUtils.copyFile(this.tables[i].get(key), 
                            this.output.get(destNo * this.tables.length + i).replace("**", key));
                }
            }
        }
    }

}
