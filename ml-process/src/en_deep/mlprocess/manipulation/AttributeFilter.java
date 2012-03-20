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
import en_deep.mlprocess.utils.Pair;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.AddValues;

/**
 * This creates new attributes that contain only the most common values of the old ones. Everything else is
 * filtered out of the old attributes.
 *
 * @author Ondrej Dusek
 */
public class AttributeFilter extends MergedHeadersOutput {

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
    /** The "add_other_val" parameter name */
    private static final String ADD_OTHER_VAL = "add_other_val";

    /** The string that is appended to all filtered attribute names */
    private static final String ATTR_NAME_SUFF = "_filt";

    /** The attribute value to replace all the filtered-out values */
    public static final String OTHER_VALUE = "[OTHER]";

    /* DATA */

    /** Add the {@link #OTHER_VALUE} to all nominal attributes ? */
    private boolean addOtherVal;
    /** Delete the original attributePrefixes ? */
    private boolean delOrig;
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
     * If more parameters are set, all conditions must be fulfilled, so that the value is not discarded.
     * </p><p>
     * There are additional parameters:
     * <p>
     * <ul>
     * <li><tt>del_orig</tt> -- delete the original attributePrefixes and keep only the filtered</li>
     * <li><tt>merge_inputs</tt> -- merge all the inputs before processing (inputs are assumed to have the same format,
     *      including the possible values)</li>
     * <li><tt>info_file</tt> -- if set, the number of inputs must be one/twice bigger (depends on <tt>merge_inputs</tt>)
     * and the last input(s) are considered to be saved information about the filtering process. This will
     * then ignore all other parameters (except <tt>del_orig</tt> and <tt>merge_inputs</tt>) and perform the process
     * exactly as instructed in the file.</li>
     * <li><tt>output_info</tt> -- if set, the number of outputs must be one/twice bigger (depends on <tt>merge_inputs</tt>)
     * and the last outputs(s) are considered to be output files where the processing info about this filtering
     * is saved for later use.</tt></li>
     * <li><tt>add_other_val</tt> -- adds {@link #OTHER_VALUE} to the list of acceptable values for all attributes</li>
     * </ul>
     * <p>
     * TODO: more meaningful add_other_val -- first see if the filtering has had some effect, then add other value
     * only if it's needed, or if it's forced
     * </p>
     */
    public AttributeFilter(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {
        
        super(id, parameters, input, output);

        // check parameters
        if (this.hasParameter(MOST_COMMON)){
            this.mostCommon = this.getIntParameterVal(MOST_COMMON);
        }
        if (this.hasParameter(MIN_OCCURRENCES)){
            this.minOccurrences = this.getIntParameterVal(MIN_OCCURRENCES);
        }
        if (this.hasParameter(MIN_PERCENTAGE)){
            this.minPercentage = this.getDoubleParameterVal(MIN_PERCENTAGE) / 100.0;
        }

        if (this.hasParameter(ATTRIBUTES)){
            this.attributePrefixes = this.parameters.get(ATTRIBUTES).split("\\s+");
            this.checkPrefixes();
        }

        this.delOrig = this.getBooleanParameterVal(DEL_ORIG);
        this.addOtherVal = this.getBooleanParameterVal(ADD_OTHER_VAL);

        if (!this.hasInfoIn() && (this.attributePrefixes == null
                || (this.mostCommon == -1 && this.minOccurrences == -1 && this.minPercentage == Double.NaN))){
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

    /**
     * This filters just one attribute of the given name, which must be present in the data
     * and nominal. If the original set of values contains some values not present in the filtered
     * set, the {@link #OTHER_VALUE} must be in the set of allowed attributes.
     *
     * @param attrPrefix the name of the attribute
     * @param data the data for which the filtering should apply
     * @param newName the new name, if left blank, a name with {@link #ATTR_NAME_SUFF} will be assigned
     * @param deleteOriginal if true, the original attribute will be removed
     * @throws NumberFormatException
     * @throws TaskException
     * @return the new name of the filtered attribute
     */
    private String filterAttribute(String attrName, String newName, Instances[] data,
            Vector<String> allowedValues, boolean deleteOriginal) throws
            NumberFormatException, TaskException {

        if (newName == null || newName.equals("") || newName.equals(attrName)){
            newName = attrName + ATTR_NAME_SUFF;
        }

        // create a unique name for the new attribute, if there is a collision
        while (data[0].attribute(newName) != null) {
            if (newName.endsWith(ATTR_NAME_SUFF)) {
                newName += "1";
            }
            else {
                int num = Integer.parseInt(newName.substring(newName.lastIndexOf(ATTR_NAME_SUFF) + ATTR_NAME_SUFF.length()));
                newName.replaceFirst("[0-9]+$", Integer.toString(num + 1));
            }
        }

        Attribute newAttr = new Attribute(newName, new Vector<String>(allowedValues));
        for (int i = 0; i < data.length; ++i) {
            this.addFiltered(data[i], attrName, newAttr, allowedValues);
            if (deleteOriginal) {
                // delete the old attribute if necessary
                data[i].deleteAttributeAt(data[i].attribute(attrName).index());
            }
        }

        return newName;
    }


    /**
     * This performs the filtering on all nominal attributes with the given prefix, if applicable.
     * Applies this to all data that may be from different files.
     *
     * @param data the data to be filtered
     * @param attrPrefix the prefix of attributes to be filtered
     * @return a mapping from the processed attributes names to their original names
     */
    private HashMap<String, String>  filterAttributePrefix(Instances [] data, String attrPrefix) throws TaskException {

        Vector<String> matches = new Vector<String>();
        Enumeration<Attribute> allAttribs = data[0].enumerateAttributes();
        HashMap<String, String> filtered = new HashMap<String, String>();

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
            Logger.getInstance().message(this.id + " : No nominal attribute matching " + attrPrefix + " found in data "
                    + data[0].relationName(), Logger.V_WARNING);
        }

        // filter all matching attributes
        for (String attrName : matches){

            Vector<String> vals = this.getAttributeValues(data[0], attrName);
            // collect statistics about the given attribute and create its filtered version
            int minOccur = this.getMinOccurrences(data);
            int[] stats = this.collectStatistics(data, attrName);
            Vector<String> allowedVals = this.filterValues(stats, minOccur, vals);

            allowedVals.add(OTHER_VALUE);
            String filteredName = this.filterAttribute(attrName, null, data, allowedVals, this.delOrig);
            filtered.put(filteredName, attrName);
        }

        return filtered;
    }

    /**
     * This parses the filtering information loaded from a file and applies it to the given data.
     *
     * @param data the data to be filtered
     * @param info the filtering options, as stored in a file
     * @throws TaskException
     * @throws NumberFormatException
     */
    private void filterWithInfo(Instances[] data, String info) throws TaskException, NumberFormatException {

        String[] infoLines = info.split("\\n");
        // this keeps the information about all attributes modifications: new name + allowed values
        HashMap<String, Vector<Pair<String, String>>> attribInfos = new HashMap<String, Vector<Pair<String, String>>>();

        // parse the filtering info, retain information about each attribute and what has been done to it
        // (can be multiple lines, in fact max. 2 -- original & filtered)
        for (String infoLine : infoLines) {
            String[] nameVal = infoLine.split(":", 3);
            Vector<Pair<String, String>> modifs = null;

            if (attribInfos.containsKey(nameVal[0])) {
                modifs = attribInfos.get(nameVal[0]);
            }
            else {
                modifs = new Vector<Pair<String, String>>();
                attribInfos.put(nameVal[0], modifs);
            }
            modifs.add(new Pair<String, String>(nameVal[1], nameVal[2]));
        }

        // now filter the attributes according to the retrieved information
        // for all nominal attributes
        for (String attrName : attribInfos.keySet()) {

            // check for the existence of the attribute, equal headers assumed
            Attribute attr = data[0].attribute(attrName);
            if (attr == null || !attr.isNominal()) {
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Missing attribute " + attrName);
            }

            // get a list of modifications
            Vector<Pair<String, String>> modifs = attribInfos.get(attrName);
            
            // process each modification separately; delete the original in the last round
            while (modifs.size() > 0) {

                Pair<String, String> modif = modifs.remove(modifs.size() - 1);
                Vector<String> allowedVals = this.parseAllowedValues(modif.second);

                String newName = this.filterAttribute(attrName, modif.first, data, allowedVals, modifs.isEmpty());
                if (!newName.equals(modif.first)){
                    this.renameAttribute(data, newName, modif.first);
                }
            }
        }
    }

    /**
     * This returns all the values of the given attribute.
     * @param data the data where the attribute is located
     * @param attrName the name of the attribute
     * @return all the possible values of the attribute
     */
    private Vector<String> getAttributeValues(Instances data, String attrName){

        Attribute attr = data.attribute(attrName);
        Vector<String> vals = new Vector<String>(attr.numValues());

        for (int i = 0; i < attr.numValues(); ++i){
            vals.add(attr.value(i));
        }
        return vals;
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
    private Vector<String> filterValues(int[] stats, int minOccurrences, Vector<String> vals) {

        Vector<String> allowedVals = new Vector<String>();

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
                allowedVals.add(vals.get(top[i]));
            }
        }
        else {
            for (int i = 0; i < stats.length; ++i){
                if (stats[i] > minOccurrences){
                    allowedVals.add(vals.get(i));
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
     * @param allowedValues the allowed values of the old attribute
     */
    private void addFiltered(Instances data, String attrName, Attribute newAttr, Vector<String> allowedValues) throws TaskException {
       
        Attribute oldAttr = data.attribute(attrName);
        int oldIndex = oldAttr.index();
        HashSet allowedSet = new HashSet<String>(allowedValues);

        boolean [] allowedIdxs = new boolean [oldAttr.numValues()]; // create map for faster queries
        for (int i = 0; i < oldAttr.numValues(); ++i){
            if (allowedSet.contains(oldAttr.value(i))){
                allowedIdxs[i] = true;
            }
        }

        data.insertAttributeAt(newAttr, oldIndex + 1);
        newAttr = data.attribute(oldIndex + 1);

        for (int i = 0; i < data.numInstances(); ++i){

            if (data.instance(i).isMissing(oldIndex)){
                data.instance(i).setMissing(newAttr);
                continue;
            }
            int val = (int) data.instance(i).value(oldIndex);
            
            try {
                if (allowedIdxs[val]){
                    data.instance(i).setValue(newAttr, oldAttr.value(val));
                }
                else {
                    data.instance(i).setValue(newAttr, OTHER_VALUE);
                }
            }
            // trying to be more verbose in the error message
            catch (Exception e){
                throw new TaskException(TaskException.ERR_IO_ERROR, this.id, 
                        e.getMessage() + " " + newAttr.name() + ": " + oldAttr.value(val));
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
     * Composes the filtering information that is to be printed out to a file after the filtering
     * has finished. Format: <tt>original name:new name (or empty if unchanged):values</tt>.
     * 
     * @param data the data with attributes already filtered
     * @param filtered a map of filtered attributes names to their original names
     * @return the processing information for later use with <tt>info_file</tt>
     */
    private String getFilteringInfo(Instances data, HashMap<String, String> filtered) {

        StringBuilder sb = new StringBuilder();

        Enumeration<Attribute> attrs = data.enumerateAttributes();

        while (attrs.hasMoreElements()) {
            Attribute attr = attrs.nextElement();
            if (attr.isNominal()) {

                // this attribute has been filtered, name changed
                if (filtered.containsKey(attr.name())){
                    sb.append(filtered.get(attr.name())).append(":").append(attr.name()).append(":");
                }
                // not filtered, no change in name
                else {
                    sb.append(attr.name()).append(":").append(attr.name()).append(":");
                }

                sb.append(StringUtils.join(Collections.list(attr.enumerateValues()).toArray(), ",", true))
                        .append("\n");
            }
        }

        return sb.toString();
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

    /**
     * This is the main data processing method -- it calls {@link #filterAttributePrefix(Instances[], String)} for
     * all the attribute prefixes. Data compatibility is assumed.
     * @param data the data to be processed
     */
    @Override
    protected String processData(Instances[] data, String info) throws Exception {

        if (info == null){
            HashMap<String, String> filtered = new HashMap<String, String>();

            for (int j = 0; j < this.attributePrefixes.length; ++j) {
                filtered.putAll(this.filterAttributePrefix(data, this.attributePrefixes[j]));
            }
            if (this.addOtherVal){
                this.addOtherValue(data);
            }
            return this.getFilteringInfo(data[0], filtered);
        }
        else {
            this.filterWithInfo(data, info);
            return info;
        }
    }


    /**
     * This parses the attribute values specifications and and returns the valid attribute values.
     *
     * @param values the string list of allowed attribute value labels (quoted)
     * @return a set of allowed values
     */
    private Vector<String> parseAllowedValues(String values) throws TaskException{

        // parse the values
        Vector<String> valList = StringUtils.parseCSV(values);
        Vector<String> valSet = new Vector<String>(valList.size());

        for (int i = 0; i < valList.size(); ++i){
            valSet.add(StringUtils.unquote(valList.get(i).trim()));
        }
        return valSet;
    }

    /**
     * Renames an attribute for all the given data sets.
     * @param data the data sets to be processed
     * @param oldName the old name of the attribute
     * @param newName the new name of the attribute
     */
    private void renameAttribute(Instances[] data, String oldName, String newName) {

        for (int i = 0; i < data.length; ++i) {
            data[i].renameAttribute(data[i].attribute(oldName), newName);
        }
    }

    /**
     * Add the {@link #OTHER_VALUE} to all nominal attributes in all the data sets.
     * @param data the data sets to be processed
     */
    private void addOtherValue(Instances[] data) throws Exception {

        Enumeration<Attribute> attrs = data[0].enumerateAttributes();

        while (attrs.hasMoreElements()) {

            Attribute attr = attrs.nextElement();

            if (attr.isNominal() && attr.indexOfValue(OTHER_VALUE) == -1) {

                AddValues filter = new AddValues();
                filter.setLabels(OTHER_VALUE);
                filter.setAttributeIndex(Integer.toString(attr.index()+1));
                Logger.getInstance().message("Adding " + OTHER_VALUE + " to attribute: " + attr.name(), Logger.V_INFO);
                filter.setInputFormat(data[0]);

                for (int i = 0; i < data.length; ++i){

                    String oldName = data[i].relationName();
                    data[i] = Filter.useFilter(data[i], filter);
                    data[i].setRelationName(oldName);
                }
            }
        }
    }

}
