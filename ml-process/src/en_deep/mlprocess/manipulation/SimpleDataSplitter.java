/*
 *  Copyright (c) 2009 Ondrej Dusek
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

import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.MergeManyValues;
import weka.filters.unsupervised.instance.SubsetByExpression;

/**
 * This class splits the data into equal pieces by lines.
 * @author Ondrej Dusek
 */
public class SimpleDataSplitter extends MultipleOutputsTask {

    /* CONSTANTS */

    /** The by_attribute parameter name */
    private static final String BY_ATTRIBUTE = "by_attribute";
    /** The num_parts parameter name */
    private static final String NUM_PARTS = "num_parts";
    /** The one_vs_others parameter name */
    private static final String ONE_VS_OTHERS = "one_vs_others";
    /** The name of the 'attr_rel' parameter */
    private static final String ATTR_REL = "attr_rel";
    /** The remove_attr parameter name */
    private static final String REMOVE_ATTR = "remove_attr";

    /* DATA */

    /** The selected value, if one_vs_others is set */
    private String selectedVal;

    /* METHODS */

    /**
     * This creates a new {@link SimpleDataSplitter} task.
     * <p>
     * The output specification must have a "**" pattern, in order to produce more output files. If there
     * are more input files, the exactly same number of outputs (with "**") must be given. If the inputs
     * have "*" in them, the expansion is handled upon output file name creation.
     * </p>
     * <p>
     * There are two possible parameters: just one of them must be given.
     * <ul>
     * <li><tt>by_attribute</tt> -- specifies the name of the attribute based on whose values the instances
     * should be split</li>
     * <li><tt>num_parts</tt> -- determines the number of output parts (which are split sequentially).</li>
     * </ul>
     * If <tt>by_attribute</tt> is specified, there is one more voluntary parameter:
     * <ul>
     * <li><tt>one_vs_others</tt> -- specifies the one value that should be selected out, versus all others
     * (the filename will have "others" in it)</li>
     * <li><tt>attr_rel</tt> -- enables a special mode where the values of the splitting attribute
     * are used as relation names and expansion patterns for the new files</li>
     * <li><tt>remove_attr</tt> -- if set, removes the attribute of splitting (not for one-vs-others)</li>
     * </u>
     *
     * @param id the task id
     * @param parameters the task parameters
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public SimpleDataSplitter(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {
        
        super(id, parameters, input, output);

        if ((!this.hasParameter(BY_ATTRIBUTE) && !this.hasParameter(NUM_PARTS))
                || (this.hasParameter(BY_ATTRIBUTE) && this.hasParameter(NUM_PARTS))){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter by_attribute OR num_parts" +
                    "must be set.");
        }
        if (this.hasParameter(ONE_VS_OTHERS)){
            if (this.hasParameter(BY_ATTRIBUTE)){
                this.selectedVal = this.getParameterVal(ONE_VS_OTHERS);
            }
            else {
                Logger.getInstance().message("Parameter " + ONE_VS_OTHERS + " is meaningless.", Logger.V_WARNING);
            }
        }

        if (this.input.size() != this.output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Numbers of inputs and outputs" +
                    "don't match.");
        }
    }

    
    /**
     * This splits just one file by attribute values. The attribute must be string or nominal. This reflects
     * the <tt>attr_rel</tt> setting.
     * 
     * @param inputFile the input file name
     * @param outputPattern the name pattern for creating output files
     */
    private void splitByAttribute(String inputFile, String outputPattern) throws Exception {
        
        Instances data = FileUtils.readArff(inputFile); // read input data
        Attribute splitAttrib;
        Enumeration values;


        // find out all attribute values (end if the attribute does not exist or is not string)
        if ((splitAttrib = data.attribute(this.parameters.get(BY_ATTRIBUTE))) == null
                || (values = splitAttrib.enumerateValues()) == null){

            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Attribute "
                    + this.parameters.get(BY_ATTRIBUTE) + " not found or not nominal/string.");
        }

        // filter out all attribute values and create output files for them
        while (values.hasMoreElements()){

            String value = values.nextElement().toString();
            SubsetByExpression filter = new SubsetByExpression();
            String outputFile = null;
            
            filter.setInputFormat(data);
            filter.setExpression("ATT" + (splitAttrib.index()+1) + " is '" + value + "'");

            String oldName = data.relationName();
            Instances subset = Filter.useFilter(data, filter); // filter the output

            if (this.getBooleanParameterVal(ATTR_REL)){ // the attr_rel mode
                subset.setRelationName(value);
                outputFile = StringUtils.replace(outputPattern, value);
            }
            else {  // normal case
                subset.setRelationName(oldName);
                outputFile = StringUtils.replace(outputPattern, this.outPrefix + splitAttrib.name() + "-" + value);
            }
            if (this.getBooleanParameterVal(REMOVE_ATTR)){
                subset.deleteAttributeAt(splitAttrib.index());
            }

            // write the output to the file
            if (!subset.isEmpty()){
                Logger.getInstance().message(this.id + ": splitting " + inputFile
                        + " to " + outputFile + "...", Logger.V_DEBUG);
                FileUtils.writeArff(outputFile, subset);
            }
        }
    }

    /**
     * This splits one file into two -- according to one value of one attribute (and its comparison to
     * {@link #selectedVal}).
     *
     * @param inputFile the input file
     * @param outputPattern the output file pattern
     */
    private void splitOneVsOthers(String inputFile, String outputPattern) throws Exception {

        Instances data = FileUtils.readArff(inputFile);
        Attribute splitAttrib;

        if ((splitAttrib = data.attribute(this.getParameterVal(BY_ATTRIBUTE))) == null
                || splitAttrib.indexOfValue(this.selectedVal) == -1){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute "
                    + this.getParameterVal(BY_ATTRIBUTE) + " or the value " + this.selectedVal + " missing.");
        }

        Instances positive = this.subsetByExpression(data,
                "ATT" + (splitAttrib.index()+1) + " is '" + this.selectedVal + "'");       
        Instances negative = this.subsetByExpression(data,
                "not (ATT" + (splitAttrib.index() + 1) + " is '" + this.selectedVal + "')");
        
        positive = this.mergeAttributeValues(positive, this.selectedVal, splitAttrib.index()+1, "first-last");
        negative = this.mergeAttributeValues(negative,
                splitAttrib.indexOfValue(this.selectedVal) == 0 ? splitAttrib.value(1) : splitAttrib.value(0),
                splitAttrib.index()+1,
                splitAttrib.indexOfValue(this.selectedVal) == 0 ? "1,2" :
                    "1," + (splitAttrib.indexOfValue(this.selectedVal)+1));

        Logger.getInstance().message(this.id + ": splitting " + inputFile + " in two ...", Logger.V_DEBUG);

        if (!positive.isEmpty()){
            FileUtils.writeArff(StringUtils.replace(outputPattern, this.outPrefix + splitAttrib.name() + "-"
                    + this.selectedVal), positive);
        }
        if (!negative.isEmpty()){
            FileUtils.writeArff(StringUtils.replace(outputPattern, this.outPrefix + splitAttrib.name() + "-other"),
                    negative);
        }

    }

    private Instances subsetByExpression(Instances data, String expr) throws Exception {

        SubsetByExpression filter = new SubsetByExpression();

        filter.setInputFormat(data);
        filter.setExpression(expr);
        Instances selected = Filter.useFilter(data, filter);
        selected.setRelationName(data.relationName());
        
        return selected;
    }


    /**
     * This splits just one file into a given number of parts.
     * @param inputFile the input file name
     * @param outputPattern the name pattern for creating output files
     */
    private void splitByPartsNumber(String inputFile, String outputPattern) throws Exception {

        Instances data = FileUtils.readArff(inputFile); // read input data
        int pos = 0; // position in data file at which the next part starts
        int parts = 0;

        // check parts number for validity
        try {
            parts = Integer.parseInt(this.parameters.get(NUM_PARTS));
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter " + NUM_PARTS + " must be" 
                    + " numeric.");
        }

        // write all the subsequent parts
        for (int i = 0; i < parts; ++i){

            int partLen = data.numInstances() / parts + (i < data.numInstances() % parts ? 1 : 0);
            Instances part = new Instances(data, pos, partLen);
            String outputFile = StringUtils.replace(outputPattern, this.outPrefix + Integer.toString(i));
            
            Logger.getInstance().message(this.id + ": splitting " + inputFile
                    + " to " + outputFile + "...", Logger.V_DEBUG);
            FileUtils.writeArff(outputFile, part);
            pos += partLen;
        }
    }

    /**
     * This merges the given attribute values using the MergeManyValues filter.
     *
     * @param data the data to be processed
     * @param newLabel the new (merged) value label
     * @param attIdx the index of the attribute to be processed (1-based)
     * @param range list/range of values to be merged (1-based)
     * @return the data with the merged attribute values
     */
    private Instances mergeAttributeValues(Instances data, String newLabel, int attIdx, String range) throws Exception {

        MergeManyValues filter = new MergeManyValues();
        
        filter.setLabel(newLabel);
        filter.setAttributeIndex(Integer.toString(attIdx));
        filter.setMergeValueRange(range);
        filter.setInputFormat(data);
        String oldName = data.relationName();
        data = Filter.useFilter(data, filter);
        data.setRelationName(oldName);
        return data;
    }

    /**
     * This performs the splitting of one file into the multiple outputs given by the pattern.
     * @param inputFile the input file name
     * @param outputPattern the output file pattern
     */
    protected void split(String inputFile, String outputPattern) throws Exception {
        if (this.selectedVal != null) {
            this.splitOneVsOthers(inputFile, outputPattern);
        }
        else if (this.hasParameter(SimpleDataSplitter.BY_ATTRIBUTE)) {
            this.splitByAttribute(inputFile, outputPattern);
        }
        else {
            this.splitByPartsNumber(inputFile, outputPattern);
        }
    }


    @Override
    public final void perform() throws TaskException {
        try {
            for (int i = 0; i < this.input.size(); ++i) {
                this.split(this.input.get(i), this.output.get(i));
            }
        }
        catch (TaskException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

}
