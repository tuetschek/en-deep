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

package en_deep.mlprocess.computation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This is a dummy classifier for the files where no training data are present. It just constructs
 * the value of the target class as a concatenation of some other attributes.
 *
 * @todo add some voluntary "separator" parameter, merge with GeneralClassifier since it belongs there
 * @author Ondrej Dusek
 */
public class ConcatClassifier extends Task {

    /** Name of the class_arg parameter */
    private static final String CLASS_ARG = GeneralClassifier.CLASS_ARG;
    /** The name of the 'concat' parameter */
    private static final String CONCAT = "concat";
    /** The name of the 'append' parameter */
    private static final String APPEND = "append";
    /** The 'sep' parameter name */
    private static final String SEP = "sep";

    /**
     * This creates a new ConcatClassifier task. Basically it just checks the numbers of inputs and outputs
     * (must be both 1) and the compulsory parameters:
     * <ul>
     * <li><tt>class_arg</tt> -- the name of the class argument</li>
     * <li><tt>concat</tt> -- space-separated list of attributes whose values should concatenate to create the target
     * value</li>
     * </ul>
     * There are voluntary parameters:
     * <ul>
     * <li><tt>sep</tt> -- separator for the concatenated values (empty if not set)</li>
     * <li><tt>append</tt> -- a suffix to be appended to the concatenation of attribute values</li>
     * </ul>
     * 
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    public ConcatClassifier(String id, Hashtable<String,String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check the I/O specs.
        if (this.input.size() != 1) {
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have 1 input.");
        }
        if (this.output.size() != 1) {
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Must have 1 output.");
        }
        // check the parameters
        if (!this.hasParameter(CONCAT) || !this.hasParameter(CLASS_ARG)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
    }



    @Override
    public void perform() throws TaskException {

        try {
            this.classify(this.input.get(0), this.output.get(0));
        }
        catch (TaskException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

    /**
     * This classifies all the instances in the file according to the given schema.
     * @param input the name of the input file
     * @param output the name of the output file
     */
    private void classify(String input, String output) throws Exception {

        Instances data = FileUtils.readArff(input);

        int origPos = data.numAttributes();
        if (data.attribute(this.getParameterVal(CLASS_ARG)) != null){
            origPos = data.attribute(this.getParameterVal(CLASS_ARG)).index();
            data.deleteAttributeAt(origPos);
        }

        // find all possible values
        String [] vals = this.getAllStringValues(data);
        HashSet<String> possibleVals = new HashSet<String>();
        possibleVals.addAll(Arrays.asList(vals));

        // create the attributes
        data.insertAttributeAt(new Attribute(this.getParameterVal(CLASS_ARG), new ArrayList<String>(possibleVals)), origPos);

        // set the values
        for (int i = 0; i < data.numInstances(); i++) {
            data.instance(i).setValue(origPos, vals[i]);
        }

        FileUtils.writeArff(output, data);
    }


    /**
     * This computes the string values for all the instances in the given data.
     * @param data the data to be examined
     * @return the string values of the desired constructed attribute
     * @throws TaskException
     */
    private String [] getAllStringValues(Instances data) throws TaskException {

        String [] concatAttNames = this.getParameterVal(CONCAT).split("\\s+");
        int [] indexes = new int [concatAttNames.length];

        for (int i = 0; i < indexes.length; i++) {
            try {
                indexes[i] = data.attribute(concatAttNames[i]).index();
            }
            catch (Exception e) {
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute " + concatAttNames[i]
                        + " not found in " + data.relationName());
            }
        }

        Enumeration<Instance> insts = data.enumerateInstances();
        StringBuilder sb = new StringBuilder();
        String [] vals = new String [data.numInstances()];
        int instNo = 0;
        String appendStr = this.hasParameter(APPEND) ? this.getParameterVal(APPEND) : "";
        String sep = this.hasParameter(SEP) ? this.getParameterVal(SEP) : "";

        while (insts.hasMoreElements()){

            Instance inst = insts.nextElement();
            sb.setLength(0);

            for (int i = 0; i < indexes.length; i++) {
                if (i > 0){
                    sb.append(sep);
                }
                sb.append(inst.stringValue(indexes[i]));
            }
            sb.append(appendStr);
            vals[instNo] = sb.toString();
            instNo++;
        }

        return vals;
    }
}
