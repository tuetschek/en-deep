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
import java.io.FileOutputStream;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

/**
 * This creates new attributes that contain only the most common values. Everything else is
 * filtered out of them.
 * @author Ondrej Dusek
 */
public class AttributeFilter extends Task {

    /* CONSTANTS */

    /** The "most_common" parameter name */
    private static final String MOST_COMMON = "most_common";
    /** The "min_occurrences" parameter name */
    private static final String MIN_OCCURRENCES = "min_occurrences";
    /** The "del_orig" parameter name */
    private static final String DEL_ORIG = "del_orig";
    /** The "attributes" parameter name */
    private static final String ATTRIBUTES = "attributes";
    /** The "merge_inputs" parameter name */
    private static final String MERGE_INPUTS = "merge_inputs";

    /** The string that is appended to all filtered attribute names */
    private static final String ATTR_NAME_SUFF = "filt";

    /** The attribute value to replace all the filtered-out values */
    private static final String OTHER_VALUE = "[OTHER]";

    /* DATA */

    /** Delete the original attributes ? */
    private boolean delOrig;
    /** Merge the inputs before processing ? */
    private boolean mergeInputs;
    /** How many most common feature values should be kept ? (-1 = not applied) */
    private int mostCommon = -1;
    /** What's the minimum number of occurrences a value should have to be preserved ? (-1 = not applied) */
    private int minOccurrences = -1;
    /** The names of the attributes to be filtered */
    private String [] attributes;


    /* METHODS */

    /**
     * This creates a new {@link AttributeFilter}. The class must have the same number of
     * inputs and outputs and no wildcards in file names.
     * <p>
     * There is one compulsory parameter:
     * </p>
     * <ul>
     * <li><tt>attributes</tt> -- space-separated list of attributes that should be filtered</li>
     * </ul>
     * <p>
     * There are two parameters, one of which must be set:
     * </p>
     * <ul>
     * <li><tt>most_common</tt> -- the maximum number of most common values that will be preserved</li>
     * <li><tt>min_occurrences</tt> -- minimum number of occurrences that the values must have in order to be
     *  preserved</li>
     * </ul>
     * <p>
     * If both parameters are set, both conditions must be fulfilled, so that the value is not discarded.
     * </p><p>
     * There are additional parameters:
     * <p>
     * <ul>
     * <li><tt>del_orig</tt> -- delete the original attributes and keep only the filtered</li>
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
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Invalid number specification.");
        }
        if (this.parameters.get(ATTRIBUTES) != null){
            this.attributes = this.parameters.get(ATTRIBUTES).split("\\s+");
        }

        if (this.parameters.get(DEL_ORIG) != null){
            this.delOrig = true;
        }
        if (this.parameters.get(MERGE_INPUTS) != null){
            this.mergeInputs = true;
        }

        if (this.attributes == null || (this.mostCommon == -1 && this.minOccurrences == -1)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }

        // check inputs and outputs
        if (this.input.size() == 0){
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

                    data[0] = this.readArff(this.input.get(i));

                    for (int j = 0; j < this.attributes.length; ++j){
                        this.filterAttribute(data, this.attributes[j]);
                    }

                    this.writeArff(this.output.get(i), data[0]);
                }
            }
            else {
                Vector<Instances> allData = new Vector<Instances>();

                for (int i = 0; i < this.input.size(); ++i){
                    allData.add(this.readArff(this.input.get(i)));
                }
                for (int j = 0; j < this.attributes.length; ++j){
                    this.filterAttribute(allData.toArray(new Instances[0]), this.attributes[j]);
                }

                for (int i = 0; i < this.output.size(); ++i){
                    this.writeArff(this.output.get(i), allData.get(i));
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
     * This eliminates the posibility that the given Vector contains patterns by throwing an exception.
     * @param whereFrom the Vector to be tested
     * @throws TaskException if the given Vector contains patterns
     */
    private void eliminatePatterns(Vector<String> whereFrom) throws TaskException {
        for (String str : whereFrom) {
            if (str.contains("*")) {
                throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.id, "Patterns in I/O specs.");
            }
        }
    }

    /**
     * This reads the contents of an ARFF (or convertible) data file, using WEKA code.
     *
     * @param fileName the name of the file to read
     * @return the file contents
     * @throws Exception if an I/O error occurs
     */
    private Instances readArff(String fileName) throws Exception {

        ConverterUtils.DataSource reader = new ConverterUtils.DataSource(fileName);
        Instances data = reader.getDataSet();
        reader.reset();
        return data;
    }

    /**
     * This performs the filtering on the attribute with the given name, if applicable.
     * Applies this to all data that may be from different files.
     *
     * @param data the data to be filtered
     * @param attrName the name of the feature to be filtered
     */
    private void filterAttribute(Instances [] data, String attrName) throws TaskException {

        String newName = attrName + ATTR_NAME_SUFF;

        // attribute must be found and must be nominal
        if (data[0].attribute(attrName) == null || !data[0].attribute(attrName).isNominal()){
            Logger.getInstance().message(this.id + " : attribute " + attrName + " not found in data "
                    + data[0].relationName(), Logger.V_WARNING);
            return;
        }
        // check data compatibility
        if (data.length > 1){
            for (int i = 1; i < data.length; ++i){
                if (!data[i].equalHeaders(data[0])){
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                            "Data from different files are not compatible.");
                }
            }
        }

        // create a unique name for the new attribute
        while (data[0].attribute(newName) != null){
            if (newName.endsWith(ATTR_NAME_SUFF)){
                newName += "1";
            }
            else {
                int num = Integer.parseInt(newName.substring(
                        newName.lastIndexOf(ATTR_NAME_SUFF) + ATTR_NAME_SUFF.length()));
                newName.replaceFirst("[0-9]+$", Integer.toString(num + 1));
            }
        }

        // collect statistics about the given attribute and create its filtered version
        int [] stats = this.collectStatistics(data, attrName);
        boolean [] allowedIndexes = this.filterValues(stats);
        Vector<String> allowedValues = new Vector<String>();

        for (int i = 0; i < allowedIndexes.length; ++i){
            if (allowedIndexes[i]){
                allowedValues.add(data[0].attribute(attrName).value(i));
            }
        }
        allowedValues.add(OTHER_VALUE);

        Attribute newAttr = new Attribute(newName, allowedValues);

        for (int i = 0; i < data.length; ++i){
            this.addFiltered(data[i], attrName, newAttr, allowedIndexes);
            
            if (this.delOrig){ // delete the old attribute if necessary
                data[i].deleteAttributeAt(data[i].attribute(attrName).index());
            }
        }
    }


    /**
     * This writes the given data into an ARFF file using WEKA code and closes the file
     * afterwards.
     *
     * @param fileName the file to write into
     * @param data the data to be written
     * @throws Exception if an I/O error occurs
     */
    private void writeArff(String fileName, Instances data) throws Exception {
        
        FileOutputStream os = new FileOutputStream(fileName);
        ConverterUtils.DataSink writer = new ConverterUtils.DataSink(os);

        writer.write(data);
        os.close();
    }

    /**
     * This collects the statistics about how often the individual values of an attribute appear.
     * @param data the data to be examined
     * @param attrName the attribute to collect the statistics about
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
     * @param attribute the attribute to have its values filtered
     * @param stats the statistics for this attribute
     * @return true for the values that have passed the filtering, false otherwise
     */
    private boolean [] filterValues(int[] stats) {

        boolean [] allowedVals = new boolean [stats.length];

        if (this.mostCommon > 0){
            
            int [] top = new int [this.mostCommon];

            for (int i = 0; i < top.length; ++i){
                top[i] = -1;
            }
            for (int i = 0; i < stats.length; ++i){
                if (stats[i] > this.minOccurrences 
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
                if (stats[i] > this.minOccurrences){
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
     * @param attrName the name of the old attribute
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

}
