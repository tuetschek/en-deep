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
     * This just checks if there are only two inputs and one output and no patterns in them.
     * @todo rename class_arg to class_attr
     */
    protected GeneralClassifier(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check the number of inputs and outputs
        if (this.input.size() != 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        this.checkNumberOfOutputs();

        // check if there are no patterns in inputs and outputs
        this.eliminatePatterns(this.input);
        this.eliminatePatterns(this.output);
    }

    /**
     * Finds out which of the features is the target one and sets it as "class" in the evaluation data.
     * It is THE one feature that is present in the train dataset and missing in evaluation dataset, or the
     * one set-up in the class_arg parameter, if the train and evaluation datasets have equal features.
     * If both data sets have equal features and no parameter is given, the last feature in train is selected.
     * If two or more features from train are missing in evaluation, an error is raised. If the attribute is
     * missing in the evaluation dataset, it is created with empty values. If the class attribute is present
     * in both data sets, it is overwritten in the evaluation data (so that new possible values are added).
     *
     * @param train the training data
     * @param eval the evaluation data
     */
    protected void findTargetFeature(Instances train, Instances eval) throws TaskException {

        Enumeration trainAtts = train.enumerateAttributes();
        Attribute missing = null;

        // find out which attribute is missing
        while (trainAtts.hasMoreElements()) {

            Attribute att = (Attribute) trainAtts.nextElement();

            if (eval.attribute(att.name()) == null) {
                if (missing == null) {
                    missing = att;
                }
                else {
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Other attribute "
                            + "than class is missing from the evaluation data.");
                }
            }
        }

        // an attribute name was given in parameters -- try to find it
        if (this.parameters.get(CLASS_ARG) != null) {

            String classArg = this.parameters.remove(CLASS_ARG);

            if (missing != null && !missing.name().equals(classArg)) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Other attribute than"
                        + " class attribute missing from the evaluation data.");
            }
            if (train.attribute(classArg) == null) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Target attribute not found "
                        + "in training data.");
            }
            if (missing == null) { // ensure the class attribute will be overwritten
                eval.deleteAttributeAt(eval.attribute(classArg).index());
            }
            missing = train.attribute(classArg);
        }
        // no attribute from train is missing in eval, no attribute preset -> select the last one
        else if(missing == null) {
            missing = train.attribute(train.numAttributes() - 1);
            eval.deleteAttributeAt(eval.attribute(missing.name()).index());
        }
        
        Attribute att = missing.copy(missing.name());
        eval.insertAttributeAt(att, missing.index());
        eval.setClassIndex(missing.index());
        train.setClassIndex(missing.index());
    }

    /**
     * This just performs the classification, while treating the first input as training data, the second
     * as evaluation data. The first output is considered to be the only output file.
     * @throws TaskException
     */
    @Override
    public final void perform() throws TaskException {
        try {
            this.classify(this.input.get(0), this.input.get(1), this.output.get(0));
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
     * @param evalFile the evaluation data file name
     * @param outputFile the output file name
     * @throws Exception if an I/O or classification error occurs
     */
    protected abstract void classify(String trainFile, String evalFile, String outputFile) throws Exception;


    /**
     * This issues an exception if the number of outputs is not correct.
     */
    protected void checkNumberOfOutputs() throws TaskException {
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Must have 1 output.");
        }
    }

}
