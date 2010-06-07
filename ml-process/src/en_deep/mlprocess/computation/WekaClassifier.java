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
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.classifiers.AbstractClassifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

/**
 * This task runs a specified weka classifier with given parameters on the given train and adds its result
 * as a separate feature to the train.
 *
 * @author Ondrej Dusek
 */
public class WekaClassifier extends Task {

    /* CONSTANTS */

    /** Name of the weka_class parameter */
    static final String WEKA_CLASS = "weka_class";

    /** Name of the class_arg parameter */
    public static final String CLASS_ARG = "class_arg";

    /** Name of the select_args parameter */
    static final String SELECT_ARGS = "select_args";

    /* DATA */

    private AbstractClassifier classif;

    /* METHODS */
 
    /**
     * This creates a new instance of {@link WekaClassifier}. It does just pattern and parameters
     * check. 
     * <p>
     * There must be no patterns in the input and output specifications, the number of inputs
     * must 2, the number of outputs must be 1 and there are no patterns allowed in inputs and outputs.
     * The first input is used as training data and the second as evaluation data.
     * </p>
     * <p>
     * There is one compulsory parameter:
     * </p>
     * <ul>
     * <li><tt>weka_class</tt> -- the desired WEKA classifier to be used</li>
     * </ul>
     * <p>
     * The following parameters are optional:
     * </p>
     * <ul>
     * <li><tt>class_arg</tt> -- the name of the target argument used for classification. If the parameter
     * is not specified, the one argument that is missing from the evaluation data will be selected. If
     * the training and evaluation data have the same arguments, the last one is used.</li>
     * <li><tt>select_args</tt> -- preselection of arguments to be used (space-separated zero-based NUMBERS
     * -- attributes order in training data, the attributes in evaluation data with the same NAMES are removed)</li>
     * </ul>
     * <p>
     * All other parameters are treated as parameters of the corresponding WEKA class, e.g. if there is
     * a parameter with the name "X", it's passed to the weka class as "-X". Parameters with empty value
     * are used as switches (e.g. param X="").
     * Some of these WEKA parameters may be compulsory to the classifier, too. See the particular
     * classifier definition to check what parameters are possible.
     * </p>
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    public WekaClassifier(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check for parameters
        if (this.parameters.get(WEKA_CLASS) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter weka_class is missing.");
        }

        // initialize the classifier and set its parameters
        this.initClassifier();

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


    @Override
    public void perform() throws TaskException {

        try {
            this.classify(this.input.get(0), this.input.get(1), this.output.get(0));
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
     * Initialize the classifier and set its parameters. For details on classifier parameters,
     * see {@link WekaClassifier(String, Hashtable, Vector, Vector)}.
     *
     * @throws TaskException
     */
    private void initClassifier() throws TaskException {

        // try to create the classifier corresponding to the given WEKA class name
        try {
            Class classifClass = null;
            Constructor classifConstructor = null;
            classifClass = Class.forName(this.parameters.get(WEKA_CLASS));
            classifConstructor = classifClass.getConstructor();
            this.classif = (AbstractClassifier) classifConstructor.newInstance();
        }
        catch (Exception e) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                    "WEKA class not found or not valid: " + this.parameters.get(WEKA_CLASS));
        }

        // set-up the classifier parameters
        Vector classifParams = new Vector<String>(this.parameters.size() - 2);
        Enumeration<String> allParams = this.parameters.keys();

        while (allParams.hasMoreElements()){
           
           String name = allParams.nextElement();
           String value;

           // skip the reserved parameters
           if (name.equals(WEKA_CLASS) || name.equals(SELECT_ARGS) || name.equals(CLASS_ARG)){
               continue;
           }

           value = this.parameters.get(name);

           if (value.equals("")){
               classifParams.add("-" + name);
           }
           else {
               classifParams.add("-" + name);
               classifParams.add(value);
           }
        }

        try {
            this.classif.setOptions((String []) classifParams.toArray(new String[0]));
        }
        catch (Exception e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Could not set classifier parameters.");
        }
    }

    /**
     * This processes on file using the given WEKA classifier.
     *
     * @param trainFile the training train file name
     * @param evalFile the evaluation train file name
     * @param outFile the output file name
     */
    private void classify(String trainFile, String evalFile, String outFile) throws Exception {

        Logger.getInstance().message(this.id + ": training " + trainFile + " for eval on " + evalFile + "...",
                Logger.V_DEBUG);

        // read the training data
        ConverterUtils.DataSource dataIn = new ConverterUtils.DataSource(trainFile);
        Instances train = dataIn.getDataSet();
        dataIn.reset();

        // read the evaluation train and find out the target class
        dataIn = new ConverterUtils.DataSource(evalFile);
        Instances eval = dataIn.getDataSet();
        dataIn.reset();

        this.findTargetFeature(train, eval);

        // pre-select the attributes
        this.attributesPreselection(train, eval);

        // train the classifier
        this.classif.buildClassifier(train);

        Logger.getInstance().message(this.id + ": evaluation on " + evalFile + "...", Logger.V_DEBUG);

        // use the classifier and store the results       
        Enumeration instances;
        
        instances = eval.enumerateInstances();
        while(instances.hasMoreElements()){
            
            Instance inst = (Instance) instances.nextElement();
            double val = this.classif.classifyInstance(inst);
            inst.setClassValue(val);
        }
        
        // write the output
        FileOutputStream os = new FileOutputStream(outFile);
        ConverterUtils.DataSink dataOut = new ConverterUtils.DataSink(os);
        dataOut.write(eval);
        os.close();

        Logger.getInstance().message(this.id + ": results saved to " + outFile + ".", Logger.V_DEBUG);
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
    private void findTargetFeature(Instances train, Instances eval) throws TaskException {

        Enumeration trainAtts = train.enumerateAttributes();
        Attribute missing = null;

        // find out which attribute is missing
        while (trainAtts.hasMoreElements()){

            Attribute att = (Attribute) trainAtts.nextElement();

            if (eval.attribute(att.name()) == null){
                if (missing == null){
                    missing = att;
                }
                else {
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Cannot find target attribute.");
                }
            }
        }

        // an attribute name was given in parameters -- try to find it
        if (this.parameters.get(CLASS_ARG) != null){

            if (missing != null && !missing.name().equals(this.parameters.get(CLASS_ARG))){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Cannot find target attribute.");
            }
            if (train.attribute(this.parameters.get(CLASS_ARG)) == null){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                        "Target attribute not found in training data.");
            }
            if (missing == null){
                train.setClass(train.attribute(this.parameters.get(CLASS_ARG)));
                eval.setClass(eval.attribute(this.parameters.get(CLASS_ARG)));
                return;
            }
            missing = train.attribute(this.parameters.get(CLASS_ARG));
        }

        // no attribute from train is missing in eval
        if (missing == null){
            train.setClassIndex(train.numAttributes()-1);
            eval.setClass(eval.attribute(train.attribute(train.numAttributes()-1).name()));
        }
        // there's just one attribute from train that's missing in eval
        else {
            Attribute att = missing.copy(missing.name());

            eval.insertAttributeAt(att, eval.numAttributes());
            eval.setClass(att);
            train.setClass(missing);
        }
    }

    /**
     * This selects only the given attributes if there is a {@link #SELECT_ARGS} setting.
     * 
     * @param train the training data
     * @param eval the evaluation data
     */
    private void attributesPreselection(Instances train, Instances eval) {

        String [] selection;
        boolean [] selectionMask = new boolean [train.numAttributes()];

        if (this.parameters.get(SELECT_ARGS) == null){
            return;
        }
        selection = this.parameters.get(SELECT_ARGS).split("\\s+");

        // find the attributes selected for removal
        for (String attr : selection){

            int attrNo = -1;

            try {
                attrNo = Integer.valueOf(attr);
            }
            catch(NumberFormatException e){
                Logger.getInstance().message(this.id + " preselected attribute " + attr + " is not a number.",
                        Logger.V_WARNING);
                continue;
            }

            if (attrNo >= 0 && attrNo < train.numAttributes()){
                selectionMask[attrNo] = true;
            }
            else {
                Logger.getInstance().message(this.id + " preselected attribute " + attr + " out of range.",
                        Logger.V_WARNING);
            }
        }
        selectionMask[train.classIndex()] = true;

        // remove the selected attributes
        for (int i = selectionMask.length - 1; i >= 0; --i){
            if (!selectionMask[i]){

                String attrName = train.attribute(i).name();

                train.deleteAttributeAt(i);
                if (eval.attribute(attrName) != null){
                    eval.deleteAttributeAt(eval.attribute(attrName).index());
                }
            }
        }
    }

}
