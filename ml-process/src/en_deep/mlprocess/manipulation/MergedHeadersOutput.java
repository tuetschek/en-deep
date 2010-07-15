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

import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This contains a useful function for tasks that need to merge the headers of some files before processing them.
 * @author Ondrej Dusek
 */
public abstract class MergedHeadersOutput extends Task {
    /**
     * The "merge_inputs" parameter name
     */
    private static final String MERGE_INPUTS = "merge_inputs";
    
    /* CONSTANTS */

    /* DATA */

    /** All values of all nominal attributes */
    HashMap<String, HashSet<String>> nominalValues;
    /** Should inputs be merged ? */
    private boolean mergeInputs;

    /* METHODS */

    /**
     * This just checks and saves the value of the mergeInputs parameter.
     */
    protected MergedHeadersOutput(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);
        this.nominalValues = new HashMap<String, HashSet<String>>();

        this.mergeInputs = this.getBooleanParameterVal(MERGE_INPUTS);
    }


    /**
     * This merges all headers of all given instances -- ie\. merges the sets of possible nominal attribute values.
     * @param data the input data
     * @throws Exception
     */
    protected void mergeHeaders(Instances [] data) throws Exception {

        boolean allEqual = true;

        for (int i = 0; i < data.length; ++i){
            if (data[0].numAttributes() != data[i].numAttributes()){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Not all data files have"
                        + " the same number of attributes.");
            }
            if (!data[0].equalHeaders(data[i])){
                allEqual = false;
            }
        }

        if (allEqual){ // nothing to do, all headers are equal
            return;
        }

        Enumeration<Attribute> attrs = data[0].enumerateAttributes();
        while (attrs.hasMoreElements()){
            
            String attrName = attrs.nextElement().name();
            int attrType = data[0].attribute(attrName).type();

            for (int i = 0; i < data.length; ++i){
                Attribute attr = data[i].attribute(attrName);

                if (attr == null || attr.type() != attrType){
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute " + attrName
                            + " missing or of a different type in some data files.");
                }
                if (attr.isNominal()){ // find all possible values of all nominal attributes
                    
                    if (this.nominalValues.get(attrName) == null){
                        this.nominalValues.put(attrName, new HashSet<String>());
                    }
                    HashSet<String> valSet = this.nominalValues.get(attrName);
                    Enumeration<String> curVals = attr.enumerateValues();
                    while (curVals.hasMoreElements()){
                        String curVal = curVals.nextElement();
                        valSet.add(curVal);
                    }
                }
            }
        }
        for (int i = 0; i < data.length; ++i){

            attrs = data[0].enumerateAttributes();
            while (attrs.hasMoreElements()){
                String attrName = attrs.nextElement().name();

                Attribute attr = data[i].attribute(attrName);

                if (attr.isNominal()){ // for all nominal attributes, set their values as a union of all possible, recount
                    String [] newValues = this.nominalValues.get(attrName).toArray(new String[0]);
                    Arrays.sort(newValues);
                    int [] remap = new int [attr.numValues()];
                    for (int j = 0; j < newValues.length; ++j){
                        if (attr.indexOfValue(newValues[j]) != -1){
                            remap[attr.indexOfValue(newValues[j])] = j;
                        }
                    }
                    int idx = attr.index();
                    double [] attData = data[i].attributeToDoubleArray(idx);
                    data[i].deleteAttributeAt(idx);
                    data[i].insertAttributeAt(new Attribute(attrName, Arrays.asList(newValues)), idx);
                    for (int j = 0; j < data[i].numInstances(); ++j){
                        data[i].get(j).setValue(idx, remap[(int) attData[j]]);
                    }
                }
            }
        }
    }

    @Override
    public final void perform() throws TaskException {
        try {
            if (!this.mergeInputs) {
                for (int i = 0; i < this.input.size(); ++i) {

                    Instances[] data = new Instances[1];

                    data[0] = FileUtils.readArff(this.input.get(i));
                    this.processData(data);
                    FileUtils.writeArff(this.output.get(i), data[0]);
                }
            }
            else {
                Vector<Instances> allData = new Vector<Instances>();

                for (int i = 0; i < this.input.size(); ++i) {
                    allData.add(FileUtils.readArff(this.input.get(i)));
                }

                Instances[] data = allData.toArray(new Instances[0]);

                this.mergeHeaders(data);
                this.processData(data);

                for (int i = 0; i < this.output.size(); ++i) {
                    FileUtils.writeArff(this.output.get(i), data[i]);
                }
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

    /**
     * This does the actual data processing and should be implemented by the derived classes.
     * @param data the data to be processed
     * @param param processing parameters
     */
    protected abstract void processData(Instances [] data) throws Exception;
}
