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

import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.Logger;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;
import weka.filters.Filter;
import weka.filters.unsupervised.instance.SubsetByExpression;

/**
 * This class splits the data into equal pieces by lines.
 * @author Ondrej Dusek
 */
public class DataSplitter extends Task {

    /* CONSTANTS */

    /** The by_attribute parameter name */
    private static final String BY_ATTRIBUTE = "by_attribute";
    /** The num_parts parameter name */
    private static final String NUM_PARTS = "num_parts";

    /* DATA */

    /* METHODS */

    /**
     * This creates a new {@link DataSplitter} task. 
     * <p>
     * The output specification must have a "**" pattern, in order to produce more output files. If there
     * are more input files, the exactly same number of outputs (with "**") must be given.
     * </p><p>
     * There are two possible parameters: by_attribute or num_parts, just one of them must be given.
     * </p><p>
     * If the by_attribute parameter is present, it specifies the attribute based on whose values the
     * instances should be split.
     * </p><p>
     * If the num_parts parameter is specified, it determines the number of output parts (which are split
     * sequentially).
     * </p>
     *
     * @param id the task id
     * @param parameters the task parameters
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public DataSplitter(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {
        
        super(id, parameters, input, output);
        if (parameters.size() != 1 || (!parameters.containsKey(BY_ATTRIBUTE) && !parameters.containsKey(NUM_PARTS))){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }
        if (input.size() != output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        // checks if there are "**" patterns in outputs (just simple string check is sufficient, Task expansion
        // ensures that there are no unwanted "*"'s.
        for (String outputFile: this.output){
            if (!outputFile.contains("**")){
                throw new TaskException(TaskException.ERR_OUTPUT_PATTERNS, this.id);
            }
        }
    }


    /**
     * Performs the task operation -- splits all the given input file(s) according to the parameters.
     * @throws TaskException
     */
    @Override
    public void perform() throws TaskException {

        try {
            for (int i = 0; i < this.input.size(); ++i){
                if (this.parameters.containsKey(BY_ATTRIBUTE)){
                        this.splitByAttribute(this.input.get(i), this.output.get(i));
                }
                else {
                    this.splitByPartsNumber(this.input.get(i), this.output.get(i));
                }
            }
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e){
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id);
        }
    }

    
    /**
     * This splits just one file by attribute values. The attribute must be string or nominal.
     * @param inputFile the input file name
     * @param outputPattern the name pattern for creating output files
     */
    private void splitByAttribute(String inputFile, String outputPattern) throws Exception {

        ConverterUtils.DataSource source = new ConverterUtils.DataSource(inputFile);
        Instances data = source.getDataSet(); // read input data
        Attribute splitAttrib;
        Enumeration values;


        // find out all attribute values (end if the attribute does not exist or is not string)
        if ((splitAttrib = data.attribute(this.parameters.get(BY_ATTRIBUTE))) == null
                || (values = splitAttrib.enumerateValues()) == null){

            Logger.getInstance().message(this.id + ": attribute " + this.parameters.get(BY_ATTRIBUTE)
                    + " not found or not string.", Logger.V_IMPORTANT);
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }

        // filter out all attribute values and create output files for them
        while (values.hasMoreElements()){

            String value = values.nextElement().toString();
            SubsetByExpression filter = new SubsetByExpression();
            String outputFile = outputPattern.replace("**", splitAttrib.name() + "-" + value);
            FileOutputStream os = null;
            
            filter.setInputFormat(data);
            filter.setExpression("ATT" + (splitAttrib.index()+1) + " is '" + value + "'");
            Instances subset = Filter.useFilter(data, filter); // filter the output

            Logger.getInstance().message(this.id + ": splitting " + inputFile
                    + " to " + outputFile + "...", Logger.V_DEBUG);
            ConverterUtils.DataSink out = new ConverterUtils.DataSink(os = new FileOutputStream(outputFile));

            // write the output to the file
            out.write(subset);
            os.close();
        }
    }


    /**
     * This splits just one file into a given number of parts.
     * @param inputFile the input file name
     * @param outputPattern the name pattern for creating output files
     */
    private void splitByPartsNumber(String inputFile, String outputPattern) throws Exception {

        FileInputStream is = new FileInputStream (inputFile);
        ConverterUtils.DataSource source = new ConverterUtils.DataSource(is); 
        Instances data = source.getDataSet(); // read input data
        int pos = 0; // position in data file at which the next part starts
        int parts = 0;

        is.close();

        // check parts number for validity
        try {
            parts = Integer.parseInt(this.parameters.get(NUM_PARTS));
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }

        // write all the subsequent parts
        for (int i = 0; i < parts; ++i){

            int partLen = data.numInstances() / parts + (i < data.numInstances() % parts ? 1 : 0);
            Instances part = new Instances(data, pos, partLen);
            String outputFile = outputPattern.replace("**", Integer.toString(i));
            FileOutputStream os = new FileOutputStream(outputFile);
            ConverterUtils.DataSink out = new ConverterUtils.DataSink(os);

            Logger.getInstance().message(this.id + ": splitting " + inputFile
                    + " to " + outputFile + "...", Logger.V_DEBUG);
            out.write(part);
            os.close();
            pos += partLen;
        }
    }



}
