/*
 *  Copyright (c) 2010-2011 Ondrej Dusek
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
import en_deep.mlprocess.utils.Pair;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.computation.wekaclassifier.LinearSequence;
import en_deep.mlprocess.computation.wekaclassifier.Sequence;
import en_deep.mlprocess.computation.wekaclassifier.TreeReader;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.MathUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import weka.classifiers.AbstractClassifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SparseInstance;
import weka.filters.unsupervised.attribute.NominalToBinary;

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
    /** Name of the args_file parameter */
    private static final String ARGS_FILE = "args_file";
    /** The name of the 'prob_dist' parameter */
    private static final String PROB_DIST = "prob_dist";
    /** Name of the ignore_attr parameter */
    static final String IGNORE_ATTRIBS = "ignore_attr";
    /** Name of the 'binarize' parameter */
    private static final String BINARIZE = "binarize";
    /** Name of the 'num_selected' parameter */
    static final String NUM_SELECTED = "num_selected";
    /** Name of the `out_attribs' parameter */
    private static final String OUT_ATTRIBS = "out_attribs";
    /** Name of the `save_model' parameter */
    private static final String SAVE_MODEL = "save_model";
    /** Name of the `load_model' parameter */
    private static final String LOAD_MODEL = "load_model";
    /** Pattern to match input files and create output files */
    private static final String PATTERN = "pattern";
    /** Name of the 'model_sel_attr' parameter */
    private static final String MODEL_SEL_ATTR = "model_sel_attr";
    /** Name of the 'model_sel_pattern' parameter */
    private static final String MODEL_SEL_PATTERN = "model_sel_pattern";

    /** {@link TreeReader} parameter name */
    private static final String TREE_READER = "tree_reader";

    /** Key for the default model in the hash table */
    private static final String DEFAULT = "";

    /* DATA */

    /** Output probability distribution instead of classification ? */
    private boolean probabilities;
    /** Should the nominal attributes be binarized before classification ? */
    private boolean binarize;
    /** The name of the file where the used attribute indexes should be written, or null */
    private String attribsOutputFile;
    /** The name of the file where the preselected attribute indexes are */
    private String preselectedAttribFile;
    /** Name of the file where the model should be written, or null */
    private String modelOutputFile;

    /** The models (possibly multiple for the various values of the deciding attribute) */
    private Hashtable<String, Model> models;
    /** Model input data files */
    private Hashtable<String, String> modelFiles;
    /** Name of the attribute that decides which model will be used */
    private String modelSelectionAttribute;
    private Instances eval;

    /* METHODS */
 
    /**
     * This creates a new instance of {@link WekaClassifier}. It does just pattern and parameters
     * check. 
     * <p>
     * There must be no patterns in the input and output specifications, the number of inputs
     * must be &gt;= 2. The number of outputs must be the same as the number of classified data sets (i.e.
     * inputs - models). The first input is either the training data, or the stored model to be loaded
     * (in that case, also some further inputs may be used for further stored models). The remaining inputs
     * are the data to be classified.
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
     * <li><tt>args_file</tt> -- same as previous, except that now, one more input file is required
     * where the selected argument ids are stored (must be the last input specification).</li>
     * <li><tt>num_selected</tt> -- limit the number of selected attributes (either from file or parameter)</tt>
     * <li><tt>ignore_attr</tt> -- ignore these attributes (NAMES)</li>
     * <li><tt>prob_dist</tt> -- output probability distributions instead of the most likely class (must be
     * supported and/or switched on for the classifier</li>
     * <li><tt>binarize</tt> -- if set, it converts all the nominal parameters to binary, while using
     * sparse matrix to represent the result.</li>
     * <li><tt>out_attribs</tt> -- if set, there must be one additional output file into which the
     * used attributes will be output</li>
     * <li><tt>load_model</tt> -- if set, the first input(s) is(are) considered to be ready-trained model file(s).
     * Preselection and binarization settings are then taken from this file as well (i.e. all arguments except
     * <tt>prob_dist</tt>, <tt>pattern</tt> and <tt>tree_reader</tt> are ignored).</li>
     * <li><tt>model_sel_attr</tt> and <tt>model_sel_pattern</tt> -- if set, multiple models are extracted from the input
     * according to the pattern and used for classification whenever the value of the selection attribute for the current
     * instance matches their pattern expansion.</li>
     * <li><tt>save_model</tt> -- if set, the trained model is saved to a file (there must be an
     * additional output at the end of the outputs specification) for later use.</li>
     * <li><tt>pattern</tt> -- if the parameter is set and the (first) output is a pattern,
     * this is assumed to be a pattern matching the input evaluation files, so that replacements may be created
     * on the output for every one of them.</li>
     * <li><tt>tree_reader</tt> -- if set, the data is not classified sequentially, but in a DFS order of syntactic
     * trees. See {@link TreeReader#TreeReader(String, Instances, String)} for the required parameter values.</li>
     * </ul>
     * <p>
     * Parameters <tt>select_args</tt> and <tt>args_file</tt>, also <tt>out_attribs</tt> and <tt>save_model</tt>
     * are mutually exclusive.
     * </p>
     * <p>
     * All other (one-character) parameters are treated as parameters of the corresponding WEKA class,
     * e.g. if there is a parameter with the name "X", it's passed to the weka class as "-X". Parameters
     * with empty value are used as switches (e.g. <tt>param X="", ...</tt> or <tt>param X, ...</tt>).
     * </p>
     * <p>
     * Since multiple parameters of the same name are allowed for WEKA classes, but not for {@link Task}
     * classes, parameters consisting of one character and a number are passed on to the WEKA class as
     * parameters of the same name (e.g. <tt>param X1="foo", X2="bar"</tt> is passed on as <tt>-X foo -X bar</tt>).
     * </p>
     * <p>
     * Some of these WEKA parameters may be compulsory to the WEKA class, too. See the particular
     * WEKA class documentation to check what parameters are possible.
     * </p>
     *
     * @todo rename select_args to select_attrib, so that the name reflects the function
     */
    public WekaClassifier(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check for parameters
        if (!this.hasParameter(WEKA_CLASS) && !this.hasParameter(LOAD_MODEL)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter weka_class is missing.");
        }
        this.probabilities = this.parameters.remove(PROB_DIST) != null;
        this.binarize = this.parameters.remove(BINARIZE) != null;

        if (this.hasParameter(SELECT_ARGS) && this.getBooleanParameterVal(ARGS_FILE)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Select_args and args_file cannot be "
                    + "set at the same time!");
        }
        if (this.getBooleanParameterVal(ARGS_FILE)){
            this.preselectedAttribFile = this.input.remove(this.input.size()-1);
            this.parameters.remove(ARGS_FILE);
        }
        if (this.getBooleanParameterVal(OUT_ATTRIBS) && this.getBooleanParameterVal(SAVE_MODEL)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Out_attribs and save_model "
                    + "cannot be set at the same time!");
        }
        if (this.getBooleanParameterVal(OUT_ATTRIBS)){
            this.attribsOutputFile = this.output.remove(this.output.size()-1);
            this.parameters.remove(OUT_ATTRIBS);
        }
        if (this.getBooleanParameterVal(SAVE_MODEL)){
            this.modelOutputFile = this.output.remove(this.output.size()-1);
            this.parameters.remove(SAVE_MODEL);
        }
        // multiple model parameters
        if ((this.hasParameter(MODEL_SEL_ATTR) ^ this.hasParameter(MODEL_SEL_PATTERN)) == true
                || (this.hasParameter(MODEL_SEL_PATTERN) && !this.hasParameter(LOAD_MODEL))){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Load_model must be set if model_sel_pattern "
                    + "and model_sel_attr are set; model_sel_pattern and model_sel_attr must be set together.");
        }
        if (this.hasParameter(MODEL_SEL_ATTR)){
            this.modelSelectionAttribute = this.getParameterVal(MODEL_SEL_ATTR);
        }
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
        Model model = new Model();
        // put the (single) model to a default place
        this.models = new Hashtable<String, Model>(1);
        this.models.put(DEFAULT, model);

        // try to create the classifier corresponding to the given WEKA class name
        try {
            Class classifClass = null;
            Constructor classifConstructor = null;
            classifClass = Class.forName(classifName);
            classifConstructor = classifClass.getConstructor();
            model.classif = (AbstractClassifier) classifConstructor.newInstance();
        }
        catch (Exception e) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                    "WEKA class not found or not valid: " + classifName);
        }
        
        String [] classifParams = StringUtils.getWekaOptions(this.parameters);

        try {
            model.classif.setOptions(classifParams);
        }
        catch (Exception e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Could not set classifier parameters.");
        }
    }

    /**
     * This processes on file using the given WEKA classifier.
     *
     * @param trainFile the training data file name
     * @param evalFiles the evaluation data file names
     * @param outFiles the output file names
     */
    @Override
    protected void classify(String trainFile, List<String> evalFiles, List<String> outFiles) throws Exception {

        // load the classifier model from file
        if (this.getBooleanParameterVal(LOAD_MODEL)){
            
            this.models = new Hashtable<String, Model>();
            if (this.modelFiles != null){ // multiple models -- load them all
                for (String param : this.modelFiles.keySet()){
                    this.models.put(param, new Model(this.id, this.modelFiles.get(param)));
                }
            }
            else {  // just a single model
                this.models.put(DEFAULT, new Model(this.id, trainFile));
            }
        }
        // train a new model
        else {
            this.trainModel(trainFile);
        }

        // classify each data file
        for (int fileNo = 0; fileNo < evalFiles.size(); ++fileNo){
            classifyFile(evalFiles.get(fileNo), outFiles.get(fileNo));
        }

        if (this.modelOutputFile != null){
            this.models.get(DEFAULT).save(this.id, this.modelOutputFile);
        }

        // clean up
        this.models = null;
        this.modelFiles = null;
    }

    /**
     * Classify data in one ARFF file (must be compatible with the model).
     *
     * @param evalFile the data file to be classified
     * @param outFile the output file
     * @throws TaskException
     * @throws Exception
     */
    private void classifyFile(String evalFile, String outFile) throws TaskException, Exception {

        // read the evaluation data and find out the target class
        Logger.getInstance().message(this.id + ": reading " + evalFile + "...", Logger.V_DEBUG);
        Instances eval = FileUtils.readArff(evalFile);
        this.eval = eval;
        Hashtable<String, Instances> modelInputs = this.prepareModelInputs(eval);

        Logger.getInstance().message(this.id + ": evaluating " + eval.relationName() + "...", Logger.V_DEBUG);

        // use the classifier and store the results
        double[][] distributions = this.probabilities ? new double[eval.numInstances()][] : null;
        Sequence seq = this.hasParameter(TREE_READER)
                ? new TreeReader(this.id, eval, this.getParameterVal(TREE_READER)) : new LinearSequence(eval);

        for (int i = seq.getNextInstance(); i >= 0; i = seq.getNextInstance()) {

            String key = this.selectModel(eval, i);
            Model model = this.models.get(key);
            Instance modelInput = this.rewriteNeighborhood(modelInputs.get(key).get(i), model, seq.getCurNeighborhood());

            if (!this.probabilities) {
                // just set the most likely class
                double val = model.classif.classifyInstance(modelInput);
                seq.setCurrentClass(val);
            }
            else {
                // save the probability distribution aside
                distributions[i] = model.classif.distributionForInstance(modelInput);
                seq.setCurrentClass(MathUtils.findMax(distributions[i]));
            }
        }

        // store the probability distributions, if supposed to
        if (this.probabilities) {
            this.addDistributions(eval, distributions);
        }

        Logger.getInstance().message(this.id + ": saving results to " + outFile + ".", Logger.V_DEBUG);
        FileUtils.writeArff(outFile, eval);
    }


    /**
     * This selects only the given attributes if there is a {@link #SELECT_ARGS}/{@link #ARGS_FILE} setting and
     * removes all the attributes specified in the {@link #IGNORE_ATTRIBS} setting. The names of the removed
     * attributes are saved to {@link #attribsToRemove}.
     * 
     * @param train the training data
     * @return the filtered training data
     */
    private Instances attributesPreselection(Instances train) throws TaskException, IOException, Exception {

        BitSet selectionMask = new BitSet(train.numAttributes());

        if (!this.hasParameter(SELECT_ARGS) && this.preselectedAttribFile == null){
            selectionMask.set(0, train.numAttributes()); // if there are no preselected attributes, set all to true
        }
        else { // otherwise, find only the preselected attributes and set their indexes to true
            int [] selectNos = this.getSelectedAttribs();

            for (int i = 0; i < selectNos.length; ++i){

                if (selectNos[i] >= 0 && selectNos[i] < train.numAttributes()){
                    selectionMask.set(selectNos[i]);
                }
                else {
                    Logger.getInstance().message(this.id + " preselected attribute " + selectNos[i] + " out of range.",
                            Logger.V_WARNING);
                }
            }
            selectionMask.set(train.classIndex());
        }
        if (this.hasParameter(IGNORE_ATTRIBS)){ // set the ignored attributes to false
            this.removeIgnoredAttributes(train, selectionMask);
        }

        this.models.get(DEFAULT).attribsToRemove = new String [train.numAttributes()-selectionMask.cardinality()];
        int pos = 0;
        // mark the names of the removed attributes
        for (int i = train.numAttributes()-1; i >= 0; --i){
            if (!selectionMask.get(i)){
                this.models.get(DEFAULT).attribsToRemove[pos++] = train.attribute(i).name();
            }
        }
        // write the settings to a file, if needed
        if (this.attribsOutputFile != null){
            this.writeAttribs(selectionMask);
        }

        // remove all the selected attributes
        train = FileUtils.filterAttributes(train, selectionMask);

        return train;
    }

    /**
     * This removes all the attributes from the selectionMask that should be ignored according to
     * the {@link #IGNORE_ATTRIBS} setting.
     * @param train the training data
     * @param eval the evaluation data
     * @param the selection mask
     */
    private void removeIgnoredAttributes(Instances train, BitSet selectionMask) {

        String[] selection = this.parameters.remove(IGNORE_ATTRIBS).split("\\s+");

        for (int i = 0; i < selection.length; ++i) {
            if (train.attribute(selection[i]) == null) {
                Logger.getInstance().message("The ignored attribute " + selection[i] + "not present", Logger.V_WARNING);
            }
            else if (selection[i].equals(train.classAttribute().name())) {
                Logger.getInstance().message("Cannot ignore class attribute " + train.classAttribute().name(),
                        Logger.V_WARNING);
            }
            else {
                selectionMask.clear(train.attribute(selection[i]).index());
            }
        }
    }

    /**
     * This returns the pre-selected attributes if the {@link #SELECT_ARGS} or {@link #ARGS_FILE} attribute
     * is set. It also limits their number according to {@link #NUM_SELECTED}.
     * @return the list of preselected attributes
     */
    private int [] getSelectedAttribs() throws TaskException{

        int [] selectNos;

        try {
            if (this.hasParameter(SELECT_ARGS)){
                selectNos = StringUtils.readListOfInts(this.parameters.remove(SELECT_ARGS));
            }
            else {
                selectNos = StringUtils.readListOfInts(FileUtils.readString(this.preselectedAttribFile, true));
            }
        }
        catch (IOException e){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Cannot read preselected attributes "
                    + "file.");
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "The preselected attributes "
                    + "must all be numbers.");
        }

        if (this.hasParameter(NUM_SELECTED)){
            int maxSel = this.getIntParameterVal(NUM_SELECTED);
            if (maxSel < selectNos.length){
                int [] temp = new int [maxSel];
                System.arraycopy(selectNos, 0, temp, 0, maxSel);
                selectNos = temp;
            }
        }
        return selectNos;
    }

    /**
     * This adds the results of the classification -- the probability distributions of classes for each
     * instance -- to the evaluation data as new features.
     *
     * @param outData the output data
     * @param distributions the classes probability distributions for the individual instances
     */
    private void addDistributions(Instances outData, double [][] distributions) {
        
        int index = addDistributionFeatures(outData);
        int instNo = 0;

        Enumeration<Instance> instances = outData.enumerateInstances();
        while (instances.hasMoreElements()) {

            Instance inst = instances.nextElement();
            double [] dist = distributions[instNo++];
            
            for (int i = 0; i < dist.length; ++i){
                inst.setValue(index + i, dist[i]);
            }
        }

    }

    private Instances sparseNominalToBinary(Instances train) throws Exception {

        NominalToBinary ntb = new NominalToBinary();

        ntb.setInputFormat(train);
        Instances out = ntb.getOutputFormat();
        for (Instance inst : train){

            SparseInstance sparse = new SparseInstance(inst);
            
            ntb.input(sparse);
            out.add(ntb.output());
        }
        return out;
    }

    /**
     * This not only checks the number of inputs and outputs, but also handles output '**' patterns,
     * if there are any, and multiple input models, if applicable.
     * @throws TaskException
     */
    @Override
    protected void checkNumberOfOutputs() throws TaskException {

        if (this.hasParameter(PATTERN) && StringUtils.hasPatternVariables(this.output.get(0), true)){

            String pattern = StringUtils.getPath(this.parameters.remove(PATTERN));
            Vector<String> repls = new Vector<String>();

            for (String in : this.input){
                String match = StringUtils.matches(in, pattern);
                if (match != null){
                    repls.add(StringUtils.replace(this.output.get(0), match));
                }
            }
            this.output.remove(0);
            this.output.addAll(0, repls);
        }

        int numIn = this.input.size()-1;
        if (this.getBooleanParameterVal(ARGS_FILE)){
            numIn--;
        }
        int numOut = this.output.size();
        if (this.getBooleanParameterVal(OUT_ATTRIBS) || this.getBooleanParameterVal(SAVE_MODEL)){
            numOut--;
        }
        // if there are multiple models to load, find them and count them
        if (this.hasParameter(MODEL_SEL_PATTERN)){
            this.modelFiles = StringUtils.findMatchingFiles(this.input, 
                    StringUtils.getPath(this.getParameterVal(MODEL_SEL_PATTERN)));
            this.input.removeAll(this.modelFiles.values());
            numIn = this.input.size();
        }

        if (numIn != numOut){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
    }

    /**
     * This writes all the selected attribute indexes that are used for classification to
     * {@link #attribsOutputFile}.
     * @param selectionMask the selected attributes
     */
    private void writeAttribs(BitSet selectionMask) throws IOException {

        PrintStream out = new PrintStream(this.attribsOutputFile);
        boolean first = true;

        for (int i = selectionMask.nextSetBit(0); i >= 0; i = selectionMask.nextSetBit(i+1)){
            if (!first){
                out.print(" ");
            }
            first = false;
            out.print(i);
        }
        out.println();
        out.close();
    }

    @Override
    protected void checkNumberOfInputs() throws TaskException {

        int minimum = 2;
        if (this.getBooleanParameterVal(ARGS_FILE)){
            minimum++;
        }
        if (this.getBooleanParameterVal(SAVE_MODEL)){
            minimum--;
        }

        if ((this.input.size() < minimum)){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
    }

    /**
     * This removes the attributes with the given names from the given data set.
     * @param data the data set to be processed
     * @param toRemove names of attributes to be removed
     */
    private Instances removeSelected(Instances data, String[] toRemove) throws Exception {

        BitSet removeMask = new BitSet(data.numAttributes());
        removeMask.set(0, data.numAttributes());

        for (int i = 0; i < toRemove.length; ++i){
            int idx = data.attribute(toRemove[i]).index();

            if (idx == -1){
                Logger.getInstance().message(this.id + ": data set " + data.relationName() + " didn't contain"
                        + " the removed attribute " + toRemove[i], Logger.V_WARNING);
            }
            else {
                removeMask.clear(idx);
            }
        }
        return FileUtils.filterAttributes(data, removeMask);
    }

    /**
     * This trains the classifier model on the given training file. It also handles the attribute preselection
     * and saves its settings to {@link #attribsToRemove}. The original training data file format (not subject
     * to attribute removal or binarization, but with the class attribute set up) is then saved
     * to {@link #trainDataFormat}.
     * @param trainFile the name of the training data file
     */
    private void trainModel(String trainFile) throws Exception {

        // initialize the classifier and set its parameters
        this.initClassifier();

        // read the training data
        Logger.getInstance().message(this.id + ": reading " + trainFile + "...", Logger.V_DEBUG);
        Instances train = FileUtils.readArff(trainFile);
        this.findClassFeature(train);
        this.models.get(DEFAULT).dataFormat = new Instances(train, 0);

        // pre-select the attributes
        Logger.getInstance().message(this.id + ": preselecting attributes...", Logger.V_DEBUG);
        train = this.attributesPreselection(train);
        this.models.get(DEFAULT).initAttribsMask();

        if (this.binarize){ // binarize the training file, if needed
            Logger.getInstance().message(this.id + ": binarizing... (" + train.relationName() + ")", Logger.V_DEBUG);
            train = this.sparseNominalToBinary(train);
            this.models.get(DEFAULT).binarize = true;
        }

        Logger.getInstance().message(this.id + ": training on " + trainFile + "...", Logger.V_DEBUG);
        // train the classifier
        this.models.get(DEFAULT).classif.buildClassifier(train);
    }

    /**
     * Prepare the actual input to the individual classification models (for single or multiple models)
     * by setting the target class, binarizing and removing unneeded attributes.
     *
     * @param eval the evaluation data
     * @return the input data to the individual models (contains a single entry keyed under {@link #DEFAULT} if there's \
     *         only one model)
     */
    private Hashtable<String, Instances> prepareModelInputs(Instances eval) throws TaskException, Exception {

        Hashtable<String, Instances> modelInputs = new Hashtable<String, Instances>();
        Model defaultModel = this.models.elements().nextElement();

        // set the class feature in eval (must be same for all if multiple models are used)
        this.setClassFeature(defaultModel.dataFormat, eval);

        for (String key : this.models.keySet()){

            // check if the class feature is the same for all models
            if (!this.models.get(key).dataFormat.classAttribute().name()
                    .equals(defaultModel.dataFormat.classAttribute().name())){

                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Class attribute is not the same"
                        + " for all the models.");
            }

            // create copies of evaluation data for all models and prepare them
            Instances modelInput = new Instances(eval);
            modelInput = this.removeSelected(modelInput, this.models.get(key).attribsToRemove);

            // binarize, if supposed to
            if (this.models.get(key).binarize){
                Logger.getInstance().message(this.id + ": binarizing... (" + modelInput.relationName() + ")", Logger.V_DEBUG);
                modelInput = this.sparseNominalToBinary(modelInput);
            }

            modelInputs.put(key, modelInput);
        }

        return modelInputs;
    }

    /**
     * For multiple models, select the right model for the current instance based on the value of the deciding attribute.
     * For single model, always return the same.
     *
     * @param inst the data set to be classified
     * @param index index of the actual instance to be classified
     * @return the right key to the {@link #models} table for the current instance
     */
    private String selectModel(Instances inst, int index) {

        if (this.modelSelectionAttribute != null){
            return inst.get(index).stringValue(inst.attribute(this.modelSelectionAttribute));
        }
        else {
            return DEFAULT;
        }
    }

    @Override
    protected List<String> getEvalFiles() {
        if (this.modelFiles != null){
            return this.input;
        }
        else {
            return super.getEvalFiles();
        }
    }

    @Override
    protected String getTrainFile(){
        if (this.modelFiles != null){
            return null;
        }
        else {
            return super.getTrainFile();
        }
    }

    /**
     * Rewrite the values of all attributes containing the class value for the neighborhood, given
     * a list of value portions to change.
     *
     * @param in the input instance to be rewritten
     * @param model the model used to classify this instance
     * @param curNeighborhood the neighborhood class values for the current instance
     * @return the input instance, with neighborhood class values rewritten
     */
    private Instance rewriteNeighborhood(Instance in, Model model, List<Pair<Integer, double[]>> curNeighborhood) {
        
        double [] vals = in.toDoubleArray();
        for (Pair<Integer, double[]> rewrite : curNeighborhood){
           for (int i = 0; i < rewrite.second.length; ++i){
               if (model.attribsMask[rewrite.first + i] != -1){
                   vals[model.attribsMask[rewrite.first + i]] = rewrite.second[i];
               }
           }
        }
        Instance ret = new DenseInstance(1.0, vals);
        ret.setDataset(in.dataset());
        return ret;
    }

    /**
     * This comprises all the required fields for a classification model.
     */
    private class Model {

        /** The classifier */
        AbstractClassifier classif;
        /** The attributes preselection setting */
        String [] attribsToRemove;
        /** The attributes preselection mask: -1 for removed attributes, the actual position for others */
        int [] attribsMask;
        /** The training data format */
        Instances dataFormat;
        /** Binarize the data set for classification ? */
        boolean binarize;

        /** Default empty constructor */
        Model(){
        }

        /**
         * This loads the trained model from the given file. It also loads the attribute preselection settings
         * and the original data format of the train file (with the class attribute set up).
         *
         * @param taskId used only for error messages
         * @param modelFile the name of the file that contains the model and other settings.
         */
        Model(String taskId, String modelFile) throws IOException, ClassNotFoundException {

            Logger.getInstance().message(taskId + ": loading the model from " + modelFile + " ...", Logger.V_DEBUG);
            ObjectInputStream oin = new ObjectInputStream(new FileInputStream(modelFile));

            this.classif = (AbstractClassifier) oin.readObject();
            this.attribsToRemove = (String []) oin.readObject();
            this.dataFormat = (Instances) oin.readObject();
            this.binarize = oin.readBoolean();
            
            if (oin.available() > 0){ // support models with or without the attribute mask saved 
                this.attribsMask = (int []) oin.readObject();
            }
            else {
                this.initAttribsMask();
            }

            oin.close();
        }

        /**
         * Initialize the used attributes mask (with the new positions of the used ones and -1 for unused ones).
         */
        void initAttribsMask(){

            BitSet bs = new BitSet(this.dataFormat.numAttributes());
            for (String attrId : this.attribsToRemove){
                bs.set(this.dataFormat.attribute(attrId).index());
            }
            this.attribsMask = new int [this.dataFormat.numAttributes()];
            int pos = 0; // count new positions here

            for (int i = 0; i < this.attribsMask.length; ++i){
                this.attribsMask[i] = bs.get(i) ? -1 : pos++;
            }
        }

        /**
         * This saves the trained classifier model to a file.
         *
         * @param taskId used only for error messages
         * @param the output file
         */
        void save(String taskId, String modelFile) throws IOException {

            Logger.getInstance().message(taskId + ": saving the model to " + modelFile + " ...", Logger.V_DEBUG);
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(modelFile));

            out.writeObject(this.classif);
            out.writeObject(this.attribsToRemove);
            out.writeObject(this.dataFormat);
            out.writeBoolean(this.binarize);
            out.writeObject(this.attribsMask);

            out.close();
        }
    }
}
