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

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.Hashtable;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This class merges several data sets into one.
 * @author Ondrej Dusek
 */
public class DataMerger extends Task {

    /* CONSTANTS */
    
    /** Line feed character */
    private static final String LF = System.getProperty("line.separator");

    /* DATA */

    /* METHODS */

    /**
     * This creates a new {@link DataMerger} task. It doesn't take any parameter except the
     * input and output data sets' descriptions. Therefore, the number of output
     * data sources must be divisible by the number of input data sources.
     *
     * @param id the task id
     * @param parameters have no sense here
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public DataMerger(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException{

        super(id, parameters, input, output);

        if (this.input.isEmpty() || this.input.size() % this.output.size() !=  0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
    }


    /**
     * Tries to merge the input sources to the output sources.
     * Checks if the number of inputs is divisible by the number of outputs, then tries to read all the
     * inputs and write the outputs.
     *
     * @throws TaskException for wrong number of inputs, or if an I/O error occurs
     */
    @Override
    public void perform() throws TaskException {

        int ratio = this.input.size() / this.output.size();

        for (int j = 0; j < this.output.size(); ++j){

            try {
                this.mergeData(this.input.subList(ratio * j, ratio * j + ratio), this.output.get(j));
            }
            catch(TaskException e){
                throw e;
            }
            catch(Exception e){
                Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
                throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
            }
        }
    }

    /**
     * Tries to merge several input files into one output, using WEKA code.
     *
     * @todo merge possible values for attributes (by creating new attributes first and then by writing down all the data using the old ones)
     * @param in the list of input files to be merged
     * @param out the output file to write to
     */
    private void mergeData(List<String> in, String out) throws Exception {

        Instances [] data = new Instances [in.size()];

        Logger.getInstance().message(this.id + ": Merging " + StringUtils.join(in, ", ") + " to " + out + " ...",
                Logger.V_INFO);

        // read all data
        for (int i = 0; i < in.size(); i++) {
            data[i] = FileUtils.readArff(in.get(i));
        }

        // merge headers
        Instances mergedHeaders = new Instances(data[0], 0);
        for (int i = 1; i < in.size(); i++){
            this.mergeHeaders(mergedHeaders, data[i]);
        }

        // write the merged headers and all data to the output
        PrintStream os = new PrintStream(out);
        os.print(mergedHeaders.toString());

        for (int i = 0; i < data.length; i++) {
            Enumeration<Instance> insts = data[i].enumerateInstances();
            while (insts.hasMoreElements()){
                os.println(insts.nextElement().toString());
            }
        }

        os.close();
    }

    /**
     * This will merge the headers of two data sets, provided they have attributes with same names and types (not necessary
     * the same possible values.
     * @param a the first set of instances, which will then contain the result
     * @param b the second set of instances that remains unchanged by the operation
     */
    private void mergeHeaders(Instances a, Instances b) throws TaskException {

        if (a.numAttributes() != b.numAttributes()){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "The datasets "
                    + a.relationName() + " and " + b.relationName() + " don't have the same number of attributes.");
        }
        Enumeration<Attribute> attribs = b.enumerateAttributes();
        while (attribs.hasMoreElements()){
            Attribute bAttr = attribs.nextElement();
            Attribute aAttr = a.attribute(bAttr.name());

            if (aAttr == null){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute "
                        + bAttr.name() + " missing from dataset " + a.relationName() + ".");
            }
            if (aAttr.type() != bAttr.type()){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attributes "
                        + aAttr.name() + " are of different types in " + a.relationName() + " and "
                        + b.relationName() + ".");
            }

            if (aAttr.isNominal()){ // merge values for nominal attributes
                Attribute merged = this.mergeAttribute(aAttr, bAttr);
                int pos = aAttr.index();
                a.deleteAttributeAt(pos);
                a.insertAttributeAt(merged, pos);
            }
        }
    }

    /**
     * This will create a merged set of values for a nominal attribute.
     * @param a the first version of the attribute
     * @param b the second version of the attribute
     * @return the merged result
     */
    private Attribute mergeAttribute(Attribute a, Attribute b) {

        HashSet<String> values = new HashSet<String>();

        Enumeration<String> valA = a.enumerateValues();
        while (valA.hasMoreElements()){
            values.add(valA.nextElement());
        }
        Enumeration<String> valB = b.enumerateValues();
        while (valB.hasMoreElements()){
            values.add(valB.nextElement());
        }

        String [] arr = values.toArray(new String [0]);
        Arrays.sort(arr);
        return new Attribute(a.name(), Arrays.asList(arr));
    }


}
