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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This class contains various functions needed by the different classifier tasks.
 * @author Ondrej Dusek
 */
public abstract class GeneralClassifier extends Task {

    /* CONSTANTS */

    /** Name of the class_arg parameter */
    public static final String CLASS_ARG = "class_arg";


    /* METHODS */

    /**
     * This just checks if there are only two inputs and one output and no patterns in them. Checking for
     * number of inputs and outputs may be overriden in {@link #checkNumberOfInputs()} and
     * {@link #checkNumberOfOutputs()}.
     * 
     * @todo rename class_arg to class_attr
     */
    protected GeneralClassifier(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);
        this.checkNumberOfInputs();
        this.checkNumberOfOutputs();

        // check if there are no patterns in inputs and outputs
        this.eliminatePatterns(this.input);
        this.eliminatePatterns(this.output);
    }

    /**
     * This issues an exception if the number of inputs is not correct.
     * @throws TaskException
     */
    protected void checkNumberOfInputs() throws TaskException {
        // check the number of inputs and outputs
        if (this.input.size() != 2) {
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
    }

    /**
     * Finds out which one of the features is the target one in training data and sets it as "class".
     * <p>
     * By default, it is the last attribute in the training set. If the {@link #CLASS_ARG} parameter
     * is set, it is the attribute of the name given in that parameter.
     * </p>
     * @param train the training data
     */
    protected void findClassFeature(Instances train) throws TaskException {

        // select the last attribute by default
        String classArg = train.attribute(train.numAttributes()-1).name();

        // an attribute name was given in parameters -- try to find it
        if (this.hasParameter(CLASS_ARG)) {
            classArg = this.parameters.remove(CLASS_ARG);
        }

        if (train.attribute(classArg) == null) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Target attribute not found "
                    + "in training data.");
        }
        train.setClass(train.attribute(classArg));
    }

    /**
     * Given training set whose class attribute is already found, this finds and sets the class
     * attribute in the given evaluation data set. Throws an exception if the evaluation set
     * doesn't conform to training data set format.
     * @param train the training data set, with its class attribute set-up properly
     * @param eval the evaluation data set, whose class attribute needs to be set-up
     */
    protected void setClassFeature(Instances train, Instances eval) throws TaskException {

        if (eval.attribute(train.classAttribute().name()) == null){
            if (eval.numAttributes() != train.numAttributes() - 1){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Incorrect"
                        + " number of attributes in " + eval.relationName() + ".");
            }
            eval.insertAttributeAt(train.classAttribute(), train.classIndex());
            eval.setClassIndex(train.classIndex());
        }
        else {
            if (eval.numAttributes() != train.numAttributes()){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Incorrect"
                        + " number of attributes in " + eval.relationName() + ".");
            }
            if (!eval.attribute(train.classAttribute().name()).equals(train.classAttribute())){
                eval.deleteAttributeAt(eval.attribute(train.classAttribute().name()).index());
                eval.insertAttributeAt(train.classAttribute(), train.classIndex());
                eval.setClassIndex(train.classIndex());
            }
            else {
                eval.setClass(eval.attribute(train.classAttribute().name()));
            }
        }
    }

    /**
     * This just performs the classification, while treating the first input as training data, the second
     * as evaluation data. The first output is considered to be the only output file.
     * @throws TaskException
     */
    @Override
    public final void perform() throws TaskException {
        try {
            List<String> evalFiles = this.input.size() > 1 ? this.input.subList(1, this.input.size()) : null;
            this.classify(this.input.get(0), evalFiles, this.output);
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
     * This is where the actual classification takes place
     * @param trainFile the training data file name
     * @param evalFiles the evaluation data file names
     * @param outputFile the output file names
     * @throws Exception if an I/O or classification error occurs
     */
    protected abstract void classify(String trainFile, List<String> evalFiles, List<String> outputFile) throws Exception;


    /**
     * This issues an exception if the number of outputs is not correct.
     */
    protected void checkNumberOfOutputs() throws TaskException {
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Must have 1 output.");
        }
    }

}
