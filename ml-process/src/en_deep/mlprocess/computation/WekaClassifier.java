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
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.MathUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.classifiers.AbstractClassifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This task runs a specified weka classifier with given parameters on the given train and adds its result
 * as a separate feature to the train.
 *
 * @author Ondrej Dusek
 */
public class WekaClassifier extends GeneralClassifier {

    /* CONSTANTS */

    /** Name of the weka_class parameter */
    static final String WEKA_CLASS = "weka_class";

    /** Name of the select_args parameter */
    static final String SELECT_ARGS = "select_args";
    /** The name of the 'prob_dist' parameter */
    private static final String PROB_DIST = "prob_dist";
    /** Name of the ignore_attr parameter */
    private static final String IGNORE_ATTRIBS = "ignore_attr";

    /* DATA */

    /** The WEKA classifier object */
    private AbstractClassifier classif;
    /** Output probability distribution instead of classification ? */
    private boolean probabilities;

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
     * <li><tt>select_args</tt> -- preselection of attributes to be used (space-separated zero-based NUMBERS
     * -- attributes order in training data, the attributes in evaluation data with the same NAMES are removed)</li>
     * <li><tt>ignore_attr</tt> -- ignore these attributes (NAMES)</li>
     * <li><tt>prob_dist</tt> -- output probability distributions instead of the most likely class (must be
     * supported and/or switched on for the classifier</li>
     * </ul>
     * <p>
     * All other parameters are treated as parameters of the corresponding WEKA class, e.g. if there is
     * a parameter with the name "X", it's passed to the weka class as "-X". Parameters with empty value
     * are used as switches (e.g. param X="").
     * Some of these WEKA parameters may be compulsory to the classifier, too. See the particular
     * classifier definition to check what parameters are possible.
     * </p>
     *
     * @todo rename select_args to select_attrib, so that the name reflects the function
     */
    public WekaClassifier(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check for parameters
        if (this.parameters.get(WEKA_CLASS) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter weka_class is missing.");
        }
        this.probabilities = this.parameters.remove(PROB_DIST) != null;

        // initialize the classifier and set its parameters
        this.initClassifier();

    }

    /**
     * This adds the features for the various values of the target class -- in order to set the probabilities for
     * the instances and classes.
     * @param eval the evaluation data
     * @return the index of the first distribution feature
     */
    private int addDistributionFeatures(Instances eval) {

        String className = eval.classAttribute().name();
        String[] classVals = new String[eval.classAttribute().numValues()];
        Enumeration<String> vals = eval.classAttribute().enumerateValues();
        int i = 0;

        while (vals.hasMoreElements()) {
            classVals[i++] = vals.nextElement();
        }
        int classIndex = eval.classIndex();
        eval.setClassIndex(-1);
        eval.deleteAttributeAt(classIndex);
        for (i = 0; i < classVals.length; i++) {
            eval.insertAttributeAt(new Attribute(className + "_" + classVals[i]), classIndex + i);
        }
        return classIndex;
    }

    /**
     * Initialize the classifier and set its parameters. For details on classifier parameters,
     * see {@link WekaClassifier(String, Hashtable, Vector, Vector)}.
     *
     * @throws TaskException
     */
    private void initClassifier() throws TaskException {

        String classifName = this.parameters.remove(WEKA_CLASS);

        // try to create the classifier corresponding to the given WEKA class name
        try {
            Class classifClass = null;
            Constructor classifConstructor = null;
            classifClass = Class.forName(classifName);
            classifConstructor = classifClass.getConstructor();
            this.classif = (AbstractClassifier) classifConstructor.newInstance();
        }
        catch (Exception e) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                    "WEKA class not found or not valid: " + classifName);
        }
        
        String [] classifParams = StringUtils.getWekaOptions(this.parameters);

        try {
            this.classif.setOptions(classifParams);
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
    protected void classify(String trainFile, String evalFile, String outFile) throws Exception {

        Logger.getInstance().message(this.id + ": training " + trainFile + " for eval on " + evalFile + "...",
                Logger.V_DEBUG);

        // read the training data
        Instances train = FileUtils.readArff(trainFile);

        // read the evaluation train and find out the target class
        Instances eval = FileUtils.readArff(evalFile);
        Instances output;

        this.findTargetFeature(train, eval);
        output =  new Instances(eval); // save a copy w/o attribute preselection

        // pre-select the attributes
        this.attributesPreselection(train, eval);

        // train the classifier
        this.classif.buildClassifier(train);

        Logger.getInstance().message(this.id + ": evaluation on " + evalFile + "...", Logger.V_DEBUG);

        // use the classifier and store the results       
        double [][] distributions = this.probabilities ? new double [eval.numInstances()][] : null;
                
        for (int i = 0; i < eval.numInstances(); ++i){
            
            if (!this.probabilities){ // just set the most likely class
                double val = this.classif.classifyInstance(eval.get(i));
                output.get(i).setClassValue(val);
            }
             else { // save the probability distribution aside
                distributions[i] = this.classif.distributionForInstance(eval.get(i));
            }
        }

        // store the probability distributions, if supposed to
        if (this.probabilities){
            this.addDistributions(output, distributions);
        }
        
        // write the output
        FileUtils.writeArff(outFile, output);

        Logger.getInstance().message(this.id + ": results saved to " + outFile + ".", Logger.V_DEBUG);
    }

    /**
     * This selects only the given attributes if there is a {@link #SELECT_ARGS} setting and removes
     * all the attributes specified in the {@link #IGNORE_ATTRIBS} setting.
     * 
     * @param train the training data
     * @param eval the evaluation data
     */
    private void attributesPreselection(Instances train, Instances eval) throws TaskException {

        
        boolean [] selectionMask = new boolean [train.numAttributes()];

        if (this.hasParameter(IGNORE_ATTRIBS)){
            String [] selection = this.parameters.remove(IGNORE_ATTRIBS).split("\\s+");
            for (int i = 0; i < selection.length; ++i){

                if (train.attribute(selection[i]) == null || eval.attribute(selection[i]) == null){
                    Logger.getInstance().message("The ignored attribute " + selection[i] + "not present", 
                            Logger.V_WARNING);
                }
                else if(selection[i].equals(train.classAttribute().name())){
                    Logger.getInstance().message("Cannot ignore class attribute " + train.classAttribute().name(),
                            Logger.V_WARNING);
                }
                else {
                    train.deleteAttributeAt(train.attribute(selection[i]).index());
                    eval.deleteAttributeAt(eval.attribute(selection[i]).index());
                }
            }
        }

        if (!this.hasParameter(SELECT_ARGS)){
            return;
        }
        int [] selectNos;

        try {
            selectNos = StringUtils.readListOfInts(this.parameters.remove(SELECT_ARGS));
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "The preselected attributes"
                    + "must all be numbers.");
        }

        // find the attributes selected for removal
        for (int i = 0; i < selectNos.length; ++i){

            if (selectNos[i] >= 0 && selectNos[i] < train.numAttributes()){
                selectionMask[selectNos[i]] = true;
            }
            else {
                Logger.getInstance().message(this.id + " preselected attribute " + selectNos[i] + " out of range.",
                        Logger.V_WARNING);
            }
        }
        selectionMask[train.classIndex()] = true;

        // remove the not-selected attributes
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

    /**
     * This adds the results of the classification -- the probability distributions of classes for each
     * instance -- to the evaluation data as new features.
     *
     * @param output the output data
     * @param distributions the classes probability distributions for the individual instances
     */
    private void addDistributions(Instances output, double [][] distributions) {
        
        int index = addDistributionFeatures(output);
        int instNo = 0;

        Enumeration<Instance> instances = output.enumerateInstances();
        while (instances.hasMoreElements()) {

            Instance inst = instances.nextElement();
            double [] dist = distributions[instNo++];
            
            for (int i = 0; i < dist.length; ++i){
                inst.setValue(index + i, dist[i]);
            }
        }

    }

}
