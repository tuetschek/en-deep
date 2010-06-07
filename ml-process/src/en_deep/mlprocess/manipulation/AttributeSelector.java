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
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

/**
 * This class splits the data into equal pieces by lines.
 * @author Ondrej Dusek
 */
public class AttributeSelector extends Task {

    /* CONSTANTS */

    /** The "select" parameter name */
    private static final String SELECT = "select";
    /** The "omit" parameter name */
    private static final String OMIT = "omit";

    /* DATA */

    /** If this is true, given parameters are omitted, otherwise, they're the only selected */
    private boolean omit;
    /** The attributes to be selected or omitted */
    private String [] attribs;

    /* METHODS */

    /**
     * This creates a new {@link DataSplitter} task. 
     * <p>
     * The number of inputs and outputs must be exactly the same, no outputs may have "**"s in the
     * file names.
     * </p><p>
     * There are two possible parameters: <b>select</b> (just the parameters given in the value
     * are copied to the output) or <b>omit</b> (the selected parameters are omitted from the output),
     * just one of them must be given.
     * </p><p>
     * Value of parameters: space-separated list of attributes.
     * </p>
     *
     * @param id the task id
     * @param parameters the task parameters
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public AttributeSelector(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {
        
        super(id, parameters, input, output);
        if (parameters.size() != 1 || (!parameters.containsKey(SELECT) && !parameters.containsKey(OMIT))){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter select OR omit must be set.");
        }

        // parse the settings
        this.omit = parameters.containsKey(OMIT);
        if (this.omit){
            this.attribs = parameters.get(OMIT).split("\\s+");
        }
        else {
            this.attribs = parameters.get(SELECT).split("\\s+");
        }

        // check inputs & outputs
        if (input.size() != output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        // checks if there are no "**" patterns in outputs (other patterns should have been already expanded)
        for (String outputFile: this.output){
            if (outputFile.contains("**")){
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
                this.selectAttributes(this.input.get(i), this.output.get(i));
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
     * This splits just one file by attribute values. The attribute must be string or nominal.
     * @param inputFile the input file name
     * @param outputPattern the name pattern for creating output files
     */
    private void selectAttributes(String inputFile, String outputFile) throws Exception {

        FileOutputStream os = null;
        ConverterUtils.DataSource source = new ConverterUtils.DataSource(inputFile);
        ConverterUtils.DataSink out;
        Instances data = source.getDataSet(); // read input data
        HashSet<Integer> indexes = new HashSet<Integer>(this.attribs.length); // valid omitted / selected indexes

        Enumeration values;

        Logger.getInstance().message(this.id + ": selecting attributes from " + inputFile + " to "
                + outputFile + "...", Logger.V_DEBUG);

        // find out valid attribute indexes (which are to be selected or omitted), issue warning if some are not present
        for (String attribName : this.attribs){
            Attribute attrib;
            
            if ((attrib = data.attribute(attribName)) == null){
                Logger.getInstance().message(this.id + ": attribute " + attrib
                        + " not found in file " + inputFile + ".", Logger.V_WARNING);
                continue;
            }
            indexes.add(attrib.index());
        }

        // create filtered set
        for (int i = data.numAttributes() - 1; i >= 0; --i){
            if ((this.omit && indexes.contains(i)) || (!this.omit && !indexes.contains(i))){
                data.deleteAttributeAt(i);
            }
        }
        // issue warning if the set is empty
        if (data.numAttributes() == 0){
            Logger.getInstance().message(this.id + ": resulting data from " + inputFile + " is empty.", Logger.V_WARNING);
        }

        out = new ConverterUtils.DataSink(os = new FileOutputStream(outputFile));

        // write the output to the file
        out.write(data);
        os.close();
    }


}
