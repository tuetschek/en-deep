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
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This creates new attributes that contain only the most common values of the old ones. Everything else is
 * filtered out of the old attributes.
 *
 * @author Ondrej Dusek
 */
public class AttributeFilter extends Task {

    /* CONSTANTS */

    /** The "most_common" parameter name */
    private static final String MOST_COMMON = "most_common";
    /** The "min_occurrences" parameter name */
    private static final String MIN_OCCURRENCES = "min_occurrences";
    /** The 'min_percentage' parameter name */
    private static final String MIN_PERCENTAGE = "min_percentage";
    /** The "del_orig" parameter name */
    private static final String DEL_ORIG = "del_orig";
    /** The "attributes" parameter name */
    private static final String ATTRIBUTES = "attributes";
    /** The "merge_inputs" parameter name */
    private static final String MERGE_INPUTS = "merge_inputs";

    /** The string that is appended to all filtered attribute names */
    private static final String ATTR_NAME_SUFF = "_filt";

    /** The attribute value to replace all the filtered-out values */
    private static final String OTHER_VALUE = "[OTHER]";

    /* DATA */

    /** Delete the original attributePrefixes ? */
    private boolean delOrig;
    /** Merge the inputs before processing ? */
    private boolean mergeInputs;
    /** How many most common feature values should be kept ? (-1 = not applied) */
    private int mostCommon = -1;
    /** What's the minimum number of occurrences a value should have to be preserved ? (-1 = not applied) */
    private int minOccurrences = -1;
    /** What's the minimum percentage of occurrences in relation to the total number of instances, so 
     * that the value is preserved ? */
    private double minPercentage = Double.NaN;
    /** The names of the attributePrefixes to be filtered */
    private String [] attributePrefixes;


    /* METHODS */

    /**
     * This creates a new {@link AttributeFilter}. The class must have the same number of
     * inputs and outputs and no wildcards in file names.
     * <p>
     * There is one compulsory parameter:
     * </p>
     * <ul>
     * <li><tt>attributes</tt> -- space-separated list of prefixes of attributes that should be filtered</li>
     * </ul>
     * <p>
     * There are two parameters, one of which must be set:
     * </p>
     * <ul>
     * <li><tt>most_common</tt> -- the maximum number of most common values that will be preserved</li>
     * <li><tt>min_occurrences</tt> -- minimum number of occurrences that the values must have in order to be
     *  preserved</li>
     * <li><tt>min_percentage</tt> -- the minimum percentage the given value must take up in all instances in
     * order to be preserved</li>
     * </ul>
     * <p>
     * If both parameters are set, both conditions must be fulfilled, so that the value is not discarded.
     * </p><p>
     * There are additional parameters:
     * <p>
     * <ul>
     * <li><tt>del_orig</tt> -- delete the original attributePrefixes and keep only the filtered</li>
     * <li><tt>merge_inputs</tt> -- merge all the inputs before processing (inputs are assumed to have the same format,
     *      including the possible values</li>
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    public AttributeFilter(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {
        
        super(id, parameters, input, output);

        // check parameters
        try {
            if (this.parameters.get(MOST_COMMON) != null){
                this.mostCommon = Integer.parseInt(this.parameters.get(MOST_COMMON));
            }
            if (this.parameters.get(MIN_OCCURRENCES) != null){
                this.minOccurrences = Integer.parseInt(this.parameters.get(MIN_OCCURRENCES));
            }
            if (this.parameters.get(MIN_PERCENTAGE) != null){
                this.minPercentage = Double.parseDouble(this.parameters.get(MIN_PERCENTAGE)) / 100.0;
            }
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Invalid number specification.");
        }
        if (this.parameters.get(ATTRIBUTES) != null){
            this.attributePrefixes = this.parameters.get(ATTRIBUTES).split("\\s+");
            this.checkPrefixes();
        }

        if (this.parameters.get(DEL_ORIG) != null){
            this.delOrig = true;
        }
        if (this.parameters.get(MERGE_INPUTS) != null){
            this.mergeInputs = true;
        }

        if (this.attributePrefixes == null 
                || (this.mostCommon == -1 && this.minOccurrences == -1 && this.minPercentage == Double.NaN)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }

        // check inputs and outputs
        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (this.input.size () != this.output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        this.eliminatePatterns(this.input);
        this.eliminatePatterns(this.output);
    }


    @Override
    public void perform() throws TaskException {

        try {
            if (!this.mergeInputs){
                for (int i = 0; i < this.input.size(); ++i){

                    Instances [] data = new Instances[1];

                    data[0] = FileUtils.readArff(this.input.get(i));

                    for (int j = 0; j < this.attributePrefixes.length; ++j){
                        this.filterAttributePrefix(data, this.attributePrefixes[j]);
                    }

                    FileUtils.writeArff(this.output.get(i), data[0]);
                }
            }
            else {
                Vector<Instances> allData = new Vector<Instances>();

                for (int i = 0; i < this.input.size(); ++i){
                    allData.add(FileUtils.readArff(this.input.get(i)));
                }
                for (int j = 0; j < this.attributePrefixes.length; ++j){
                    this.filterAttributePrefix(allData.toArray(new Instances[0]), this.attributePrefixes[j]);
                }

                for (int i = 0; i < this.output.size(); ++i){
                    FileUtils.writeArff(this.output.get(i), allData.get(i));
                }
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
     * This filters just one attribute of the given name, which must be present in the data
     * and nominal.
     *
     * @param attrPrefix the name of the attribute
     * @param data the data for which the filtering should apply
     * @throws NumberFormatException
     * @throws TaskException
     */
    private void filterAttribute(String attrName, Instances[] data) throws NumberFormatException, TaskException {

        String newName = attrName + ATTR_NAME_SUFF;

        // create a unique name for the new attribute
        while (data[0].attribute(newName) != null) {
            if (newName.endsWith(ATTR_NAME_SUFF)) {
                newName += "1";
            } else {
                int num = Integer.parseInt(newName.substring(newName.lastIndexOf(ATTR_NAME_SUFF) + ATTR_NAME_SUFF.length()));
                newName.replaceFirst("[0-9]+$", Integer.toString(num + 1));
            }
        }
        // collect statistics about the given attribute and create its filtered version
        int minOccur = this.getMinOccurrences(data);
        int[] stats = this.collectStatistics(data, attrName);
        boolean[] allowedIndexes = this.filterValues(stats, minOccur);
        Vector<String> allowedValues = new Vector<String>();

        for (int i = 0; i < allowedIndexes.length; ++i) {
            if (allowedIndexes[i]) {
                allowedValues.add(data[0].attribute(attrName).value(i));
            }
        }
        allowedValues.add(OTHER_VALUE);

        Attribute newAttr = new Attribute(newName, allowedValues);
        for (int i = 0; i < data.length; ++i) {
            this.addFiltered(data[i], attrName, newAttr, allowedIndexes);
            if (this.delOrig) {
                // delete the old attribute if necessary
                data[i].deleteAttributeAt(data[i].attribute(attrName).index());
            }
        }
        return;
    }


    /**
     * This performs the filtering on all nominal attributes with the given prefix, if applicable.
     * Applies this to all data that may be from different files.
     *
     * @param data the data to be filtered
     * @param attrPrefix the prefix of attributes to be filtered
     */
    private void filterAttributePrefix(Instances [] data, String attrPrefix) throws TaskException {

        // check data compatibility
        if (data.length > 1) {
            for (int i = 1; i < data.length; ++i) {
                if (!data[i].equalHeaders(data[0])) {
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                            "Data from different files are not compatible.");
                }
            }
        }

        Vector<String> matches = new Vector<String>();
        Enumeration<Attribute> allAttribs = data[0].enumerateAttributes();

        // find all matching nominal attributes
        while (allAttribs.hasMoreElements()){
            Attribute a = allAttribs.nextElement();
            if (a.name().startsWith(attrPrefix) && a.isNominal()){
                matches.add(a.name());
            }
        }

        // no matching attribute found
        if (matches.isEmpty()) {
            // attribute must be found and must be nominal
            Logger.getInstance().message(this.id + " : No attribute matching " + attrPrefix + " found in data "
                    + data[0].relationName(), Logger.V_WARNING);
            return;
        }

        // filter all matching attributes
        for (String attrName : matches){
            this.filterAttribute(attrName, data);
        }
    }


    /**
     * This collects the statistics about how often the individual values of an attribute appear.
     * @param data the data to be examined
     * @param attrPrefix the attribute to collect the statistics about
     * @return the occurrence counts for all values of the given attribute in the given data
     */
    private int [] collectStatistics(Instances[] data, String attrName) {

        int [] stats = new int [data[0].attribute(attrName).numValues()];

        for (int i = 0; i < data.length; ++i){
            
            double [] values = data[i].attributeToDoubleArray(data[i].attribute(attrName).index());

            for (int j = 0; j < values.length; ++j){
                stats[(int)values[j]]++;
            }
        }

        return stats;

        
    }

    /**
     * Returns the values that have passed the filtering for the given attribute.
     *
     * @param stats the statistics for this attribute
     * @param minOccurrences the minimum number of occurrences the values must have
     * @return true for the values that have passed the filtering, false otherwise
     */
    private boolean [] filterValues(int[] stats, int minOccurrences) {

        boolean [] allowedVals = new boolean [stats.length];

        if (this.mostCommon > 0){
            
            int [] top = new int [this.mostCommon];

            for (int i = 0; i < top.length; ++i){
                top[i] = -1;
            }
            for (int i = 0; i < stats.length; ++i){
                if (stats[i] > minOccurrences 
                        && (top[this.mostCommon - 1] == -1 || stats[i] > stats[top[this.mostCommon - 1]])){
                    int j = this.mostCommon - 1;
                    while (j > 0 && (top[j] == -1 || stats[i] > stats[top[j-1]])){
                        --j;
                    }
                    System.arraycopy(top, j, top, j + 1, top.length - j - 1);
                    top[j] = i;
                }
            }
            for (int i = 0; i < top.length && top[i] != -1; ++i){
                allowedVals[top[i]] = true;
            }
        }
        else {
            for (int i = 0; i < stats.length; ++i){
                if (stats[i] > minOccurrences){
                    allowedVals[i] = true;
                }
            }
        }

        return allowedVals;
    }

    /**
     * Add the filtered attribute and filter the data.
     *
     * @param data the data where the attribute is to be inserted
     * @param attrPrefix the name of the old attribute
     * @param newAttr the new attribute
     * @param allowedIndexes indexes of the allowed values of the old attribute
     */
    private void addFiltered(Instances data, String attrName, Attribute newAttr, boolean [] allowedIndexes) {

        int oldIndex = data.attribute(attrName).index();
        
        data.insertAttributeAt(newAttr, oldIndex + 1);
        newAttr = data.attribute(oldIndex + 1);

        for (int i = 0; i < data.numInstances(); ++i){
            int value = (int) data.instance(i).value(oldIndex);
            if (allowedIndexes[value]){
                data.instance(i).setValue(newAttr, data.attribute(oldIndex).value(value));
            }
            else {
                data.instance(i).setValue(newAttr, OTHER_VALUE);
            }
        }
    }

    /**
     * Check that some of the attribute prefixes are not prefixes of others, so that the filtering is not
     * processed twice.
     */
    private void checkPrefixes() {

        Vector<Integer> banned = new Vector<Integer>();

        for (int i = 0; i < this.attributePrefixes.length; ++i){
            for (int j = 0; j < i; ++j){
                if (this.attributePrefixes[i].startsWith(this.attributePrefixes[j])){
                    banned.add(i);
                }
                else if (this.attributePrefixes[j].startsWith(this.attributePrefixes[i])){
                    banned.add(j);
                }
            }
        }

        if (banned.size() > 0){
            Logger.getInstance().message(this.id + " : Some prefixes in the 'attributes' parameter overlap.",
                    Logger.V_WARNING);

            String [] uniquePrefixes = new String [this.attributePrefixes.length - banned.size()];
            int pos = 0;

            for (int i = 0; i < this.attributePrefixes.length; ++i){

                if (!banned.contains(i)){
                    uniquePrefixes[pos] = this.attributePrefixes[i];
                    pos++;
                }
            }
            this.attributePrefixes = uniquePrefixes;
        }
    }

    /**
     * This computes the minimum number of occurrences a value must have in order to be preserved, according to
     * the {@link #minOccurrences} and {@link #minPercentage} settings.
     *
     * @param data the data to be processed
     * @return the minimum number of occurrences for the values in data
     */
    private int getMinOccurrences(Instances[] data) {

        if (this.minOccurrences == -1 && this.minPercentage == Double.NaN){
            return 0;
        }
        else if (this.minPercentage == Double.NaN){
            return this.minOccurrences;
        }
        else {
            int sumInst = 0;

            for (int i = 0; i < data.length; ++i){
                sumInst += data[i].numInstances();
            }
            
            return Math.max(this.minOccurrences, (int) Math.ceil(this.minPercentage * sumInst));
        }

    }



}
