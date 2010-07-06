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
import en_deep.mlprocess.utils.StringUtils;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This removes all the attributes that are irrelevant for the classification (attributes that have different
 * values for all instances and attributes that don't have more than one possible value).
 *
 * @author Ondrej Dusek
 */
public class IrrelevantAttributesRemoval extends Task {

    /* CONSTANTS */

    /** The name of the "preserve" parameter */
    private static final String PRESERVE = "preserve";
    /** The name of the "merge_input" parameter */
    private static final String MERGE_INPUTS = "merge_inputs";
    /** The name of the "remove" parameter */
    private static final String REMOVE = "remove";

    /* DATA */

    /** The list of attributes to be preserved */
    private HashSet<String> preserveAttribs;
    /** The list of attributes that need to be removed */
    private HashSet<String> removeAttribs;
    /** Should inputs be merged before they're considered? */
    private boolean mergeInputs;

    /**
     * Possible conditions for removal (unary attribute,
     * attribute with no identical values, preselected for removal)
     */
    private enum Condition {
        PRESELECTED,
        UNARY,
        NON_IDENTICAL;

        @Override
        public String toString() {
            switch (this){
                case PRESELECTED:
                    return "preselected";
                case UNARY:
                    return "unary";
                case NON_IDENTICAL:
                    return "non_identical";
            }
            return "";
        }
    }


    /* METHODS */

    /**
     * This just creates the new task and checks the inputs and outputs and parameters.
     * The class has no compulsory paramters. There arer voluntary parameters:
     * <ul>
     * <li><tt>preserve</tt> -- space-separated list of attributes that need to be preserved at any rate.
     * <li><tt>remove</tt> -- space-separated list of attributes that need to be removed at any rate
     * <li><tt>merge_inputs</tt> -- the inputs are merged before consideration
     * </ul>
     * The number of inputs must be the same as the number of outputs.
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    public IrrelevantAttributesRemoval(String id, Hashtable<String, String> parameters, Vector<String> input, 
            Vector<String> output) throws TaskException {
        
        super(id, parameters, input, output);

        // check the number of inputs and outputs
        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have some inputs.");
        }
        if (this.output.size() != this.input.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "The number of outputs must "
                    + "be the same as that of inputs.");
        }
        this.eliminatePatterns(input);
        this.eliminatePatterns(output);

        // check the parameters
        this.preserveAttribs = this.saveToHashSet(this.parameters.get(PRESERVE));
        this.removeAttribs = this.saveToHashSet(this.parameters.get(REMOVE));

        this.mergeInputs = this.getBooleanParameterVal(MERGE_INPUTS);
    }


    @Override
    public void perform() throws TaskException {
        
        try {
            if (!this.mergeInputs){
                for (int i = 0; i < this.input.size(); ++i){

                    Instances [] data = new Instances[1];

                    data[0] = FileUtils.readArff(this.input.get(i));

                    this.removeIrrelevant(data);

                    FileUtils.writeArff(this.output.get(i), data[0]);
                }
            }
            else {
                Vector<Instances> allData = new Vector<Instances>();

                for (int i = 0; i < this.input.size(); ++i){
                    allData.add(FileUtils.readArff(this.input.get(i)));
                }
                this.removeIrrelevant(allData.toArray(new Instances[0]));

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
     * This removes all the irrelevant attributes in all the given data sets.
     * @param data the data sets to be filtered
     */
    private void removeIrrelevant(Instances [] data) throws TaskException {

        // check data compatibility
        if (data.length > 1) {
            for (int i = 1; i < data.length; ++i) {
                if (!data[i].equalHeaders(data[0])) {
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                            "Data from different files are not compatible.");
                }
            }
        }

        // remove the attributes, whatever the cause
        Condition [] allConds = Condition.values();
        for (int i = 0; i < allConds.length; ++i){
            this.removeAttributes(data, allConds[i]);
        }
    }


    /**
     * This removes attributes that fail to fulfill the given type of condition from all the data sets.
     * @param data the data to be filtered 
     * @param condition the {@link Condition} that the attributes must fulfill
     */
    private void removeAttributes(Instances[] data, Condition condition) {

        Enumeration<Attribute> attribs = data[0].enumerateAttributes();
        Vector<String> forRemoval = new Vector<String>();

        while (attribs.hasMoreElements()) {
            Attribute attr = attribs.nextElement();

            if (!this.preserveAttribs.contains(attr.name())) {
                switch (condition){
                    case NON_IDENTICAL:
                        if (!this.checkIdenticalValues(data, attr.name())){
                            forRemoval.add(attr.name());
                        }
                        break;

                    case UNARY:
                        if (!this.checkDifferentVlaues(data, attr.name())){
                            forRemoval.add(attr.name());
                        }
                        break;
                        
                    case PRESELECTED:
                        if (this.removeAttribs.contains(attr.name())){
                            forRemoval.add(attr.name());
                        }
                        break;
                }
            }
        }
        if (!forRemoval.isEmpty()){
            Logger.getInstance().message(this.id + " : removing " + condition.toString() + " "
                    + StringUtils.join(forRemoval, ", ") + ".", Logger.V_DEBUG);
        }
        else {
            Logger.getInstance().message(this.id + " : no " + condition.toString() + " features found.",
                    Logger.V_DEBUG);
        }
        for (String attrName : forRemoval) {
            for (int i = 0; i < data.length; ++i) {
                data[i].deleteAttributeAt(data[i].attribute(attrName).index());
            }
        }
    }

    /**
     * This checks that there are two or more identical values in attribute data (for all given data sets).
     * @param data the data sets to check
     * @param name the attribute name to check
     * @return true if there are two identical values of the given attribute in the data
     */
    private boolean checkIdenticalValues(Instances[] data, String name) {

        HashSet<Double> foundValues = new HashSet<Double>();

        for (int i = 0; i < data.length; ++i){

            double [] values = data[i].attributeToDoubleArray(data[i].attribute(name).index());

            for (int j = 0; j < values.length; ++j){
                if (foundValues.contains(values[j])){ // value already encountered
                    return true;
                }
                foundValues.add(values[j]);
            }
        }
        return false;
    }

    /**
     * This checks that there are at least two different values in the attribute data (for all given data sets)
     *
     * @param data the data sets to check
     * @param name the attribute name to check
     * @return true if there are two different values of the given attribute in the data
     */
    private boolean checkDifferentVlaues(Instances[] data, String name) {

        double firstValue = Double.NaN;

        for (int i = 0; i < data.length; ++i){

            double [] values = data[i].attributeToDoubleArray(data[i].attribute(name).index());

            if (i == 0){
                firstValue = values[0];
            }
            for (int j = 0; j < values.length; ++j){
                if (values[j] != firstValue){
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Save the space-separated values of an attribute to a hash-set.
     * @param values the value of an attribute, which should contain space-separated values (may be null)
     * @return the resulting hash-set with all values saved, empty hash-set in case values is null
     */
    private HashSet<String> saveToHashSet(String values) {
        
        HashSet<String> ret = new HashSet<String>();

        if (values != null){
            String [] valArr = values.split("\\s+");

            for (int i = 0; i < valArr.length; ++i){
                ret.add(valArr[i]);
            }
        }

        return ret;
    }


}
