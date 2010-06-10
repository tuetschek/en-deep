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


    /* METHODS */


    /**
     * This just checks if there are only two inputs and one output and no patterns in them.
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    public GeneralClassifier(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check the number of inputs and outputs
        if (this.input.size() != 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }

        // check if there are no patterns in inputs and outputs
        if (this.output.get(0).contains("*")){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.id,
                    "No patterns allowed in output file name.");
        }
        for (String inputFile: this.input){
            if (inputFile.contains("*")){
                throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.id,
                        "No patterns allowed in input file names.");
            }
        }
    }

    /**
     * Finds out which of the features is the target one and sets it as "class" in the evaluation data.
     * It is THE one feature that is present in the train dataset and missing in eval dataset.
     * If the train and eval datasets have equal features, it's the last feature in train.
     * If two or more features from train are missing in eval, an error is raised.
     *
     * @param train
     * @param eval
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
                } else {
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Cannot find target attribute.");
                }
            }
        }
        // an attribute name was given in parameters -- try to find it
        if (this.parameters.get(WekaClassifier.CLASS_ARG) != null) {
            if (missing != null && !missing.name().equals(this.parameters.get(WekaClassifier.CLASS_ARG))) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Cannot find target attribute.");
            }
            if (train.attribute(this.parameters.get(WekaClassifier.CLASS_ARG)) == null) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Target attribute not found in training data.");
            }
            if (missing == null) {
                train.setClass(train.attribute(this.parameters.get(WekaClassifier.CLASS_ARG)));
                eval.setClass(eval.attribute(this.parameters.get(WekaClassifier.CLASS_ARG)));
                return;
            }
            missing = train.attribute(this.parameters.get(WekaClassifier.CLASS_ARG));
        }
        // no attribute from train is missing in eval
        if (missing == null) {
            train.setClassIndex(train.numAttributes() - 1);
            eval.setClass(eval.attribute(train.attribute(train.numAttributes() - 1).name()));
        } else {
            Attribute att = missing.copy(missing.name());
            eval.insertAttributeAt(att, eval.numAttributes());
            eval.setClass(att);
            train.setClass(missing);
        }
    }

    /**
     * This just performs the classification, while treating the first input as training data, the second
     * as evaluation data. The first output is considered to be the only output file.
     * @throws TaskException
     */
    @Override
    public void perform() throws TaskException {
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
     * @param train the training data file name
     * @param eval the evaluation data file name
     * @param output the output file name
     * @throws Exception if an I/O or classification error occurs
     */
    protected abstract void classify(String train, String eval, String output) throws Exception;

}
