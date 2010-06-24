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
import en_deep.mlprocess.Pair;
import en_deep.mlprocess.Plan;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.TaskDescription;
import en_deep.mlprocess.TaskDescription.TaskStatus;
import en_deep.mlprocess.evaluation.EvalClassification;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.MathUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Instances;


/**
 * This class applies the greedy attribute selection algorithm with the given
 * WEKA classifier type and settings on the given data.
 *
 * @author Ondrej Dusek
 */
public class GreedyAttributeSearch extends EvalSelector {

    /* CONSTANTS */


    /** The name of the reserved "round" parameter */
    private static final String ROUND = "round_number";
    /** The name of the reserved "attrib_count" parameter */
    private static final String ATTRIB_COUNT = "attrib_count";
    /** The name of the reserved "class_attr_no" parameter */
    private static final String CLASS_ATTR_NO = "class_attr_no";
    /** The name of the "attrib_order" parameter */
    private static final String ATTRIB_ORDER = "attrib_order";

    /** The name of the "start" parameter */
    private static final String START = "start";
    /** The name of the "start_attrib" parameter */
    private static final String START_ATTRIB = "start_attrib";
    /** The name of the "end" parameter */
    private static final String END = "end";
    /** The minimal improvement that is possible */
    private static final String MIN_IMPROVEMENT = "min_improvement";

    /** CR/LF */
    private static final String LF = System.getProperty("line.separator");

    /* DATA */

    /** The name of the WEKA algorithm to be used */
    private String wekaClass;

    /** The name of the class attribute to be computed and evaluated upon */
    private String classArg;

    /** The file containing the order of the attributes that should be used in evaluation */
    private String attributeOrderFile;

    /** The starting number of attributes used */
    private int start;
    /** The maximum number  of attributes used */
    private int end;
    /** The current number of attributes for this round */
    private int round;
    /** The minimal improvement of the algorithm in order to continue */
    private double minImprovement;
    /** The best result from the last round */
    private double lastBest;
    /** The bit-mask of the best attributes from the last round */
    private boolean [] lastBestAttributes;
    /** A space-separated list of the best attributes from the last round */
    private String lastBestAttributesList;
    /** The total available number of attributes */
    private int attribCount = -1;
    /** The number of the class attribute */
    private int classAttribNum = -1;
    /** A list of attributes the process should start with (if set) */
    private String [] startAttrib;

    /* METHODS */

    /**
     * <p>
     * This constructs a {@link GreedyAttributeSearch} object. The constructor just checks
     * the validity of parameters, inputs and outputs. It removes all the parameters from
     * the {@link #parameters} member, except for the classifier parameters.
     * </p>
     * <p>
     * There are the same parameters as in {@link WekaClassifier}, plus two that are same
     * as in {@link SettingSelector} -- <tt>measure</tt> and <tt>tempfile</tt>.
     * There are additional compulsory parameters (<tt>start</tt> and <tt>start_attrib</tt> are
     * mutually exclusive):
     * </p>
     * <ul>
     * <li><tt>start</tt> -- the starting number of attributes (should be very small, for all
     * possible combinations will be tried)</li>
     * <li><tt>start_arttrib</tt> -- the (space-separated) list of starting attributes</li>
     * <li><tt>end</tt> -- the maximum number of attributes that are to be tried</li>
     * </ul>
     * <p>
     * There must be exactly two inputs (first of which is the training data and second the
     * testing data) and two outputs (one is for the classification output and one for
     * the output of the best set of parameters).
     * </p>
     * <p>
     * There are optional parameters:
     * </p>
     * <ul>
     * <li><tt>min_improvement</tt> -- the minimal required improvement in the selected measure for the
     * algorithm to continue (defaults to 0)</li>
     * <li><tt>attrib_order</tt> -- if this parameter is set, the last input file is considered to be
     * the file with the desired order in which the attributes with the same performance should be added to
     * the set of used attributes.</li>
     * </li>
     * There are special parameters reserved for the program (which control the process):
     * <ul>
     * <li><tt>round_number</tt> -- the current number of attributes used (If the task
     * is run with this parameter set higher than start, more inputs are allowed)</li>
     * <li><tt>attrib_count</tt> -- the total number of possible attributes (excluding the class
     * attribute)</li>
     * <li><tt>class_attr_no</tt> -- the number of the class attribute</li>
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    public GreedyAttributeSearch(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // determine the current round and check all the round-related constraints
        this.checkRound();

        // check all round-related compulsory and optional parameters
        this.checkParameters();

        // check the number of inputs and outputs
        if (this.round == this.start && input.size() != 2 || input.size() < 2 || input.size() % 2 != 0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (output.size() != 3){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
    }

    @Override
    protected void setExpandedId() {

        this.expandedId = this.id.indexOf('#') == -1 ? "" : this.id.substring(this.id.indexOf('#'));

        if (this.expandedId.length() > 0
                && (this.expandedId.substring(this.expandedId.lastIndexOf('#')).matches("#round[0-9]+") ||
                this.expandedId.endsWith("#finalize"))) {
            this.expandedId = this.expandedId.substring(0, this.expandedId.lastIndexOf('#'));
        }

        if (this.expandedId.startsWith("#")) {
            this.expandedId = this.expandedId.substring(1);
        }
        this.expandedId = this.expandedId.replace('#', '_');
    }


    /**
     * This checks if all the needed parameters are present and saves all their values. If some parameters are incorrect
     * or missing, it throws an exception.
     *
     * @throws TaskException if some of the parameters are wrong or missing
     */
    private void checkParameters() throws TaskException {

        // final round -- special case: some parameters are not needed.
        if (this.round > this.end) {

            if (this.parameters.get(TEMPFILE) == null) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Some parameters are missing.");
            }
            this.tempFilePattern = this.parameters.remove(TEMPFILE);
            this.attributeOrderFile = this.getAttributeOrderFile(); // optional parameter
            return;
        }
        
        // normal case: check the compulsory parameters and save them
        if (this.parameters.get(WEKA_CLASS) == null || this.parameters.get(CLASS_ARG) == null 
                || this.parameters.get(TEMPFILE) == null || this.parameters.get(TEMPFILE).indexOf("*") == -1) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Some parameters are missing.");
        }

        this.classArg = this.parameters.remove(CLASS_ARG);
        this.wekaClass = this.parameters.remove(WEKA_CLASS);
        this.tempFilePattern = this.parameters.remove(TEMPFILE);

        // normal case: check the optional parameters
        if (this.parameters.get(MIN_IMPROVEMENT) != null) {
            try {
                this.minImprovement = Double.parseDouble(this.parameters.remove(MIN_IMPROVEMENT));
            }
            catch (NumberFormatException e) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                        "Parameter " + MIN_IMPROVEMENT + " must be numeric.");
            }
        }
        this.attributeOrderFile = this.getAttributeOrderFile();
    }

    /**
     * This checks all the round-related constraints and determines the number of the current, staring and ending round.
     * @throws TaskException if some of the needed parameters is missing or erroneous
     */
    private void checkRound() throws TaskException {

        // check if all needed parameters are present
        if ((this.parameters.get(START) == null && this.parameters.get(START_ATTRIB) == null)
                || (this.parameters.get(START) != null && this.parameters.get(START_ATTRIB) != null)
                || this.parameters.get(END) == null) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Some parameters are missing.");
        }

        // convert the numeric parameters for starting and ending round
        try {
            if (this.parameters.get(START) != null) {
                this.start = Integer.parseInt(this.parameters.remove(START));
            }
            else {
                this.startAttrib = this.parameters.get(START_ATTRIB).split("\\s+");
                this.start = this.startAttrib.length;
            }
            this.end = Integer.parseInt(this.parameters.remove(END));
        }
        catch (NumberFormatException e) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameters " + START + " and "
                    + END + " must be numeric.");
        }

        // this is the first round -- set the necessary parameter
        if (this.parameters.get(ROUND) == null) {
            this.round = this.start;
        } 
        // for all next rounds, get the needed parameters
        else {
            try {
                this.round = Integer.parseInt(this.parameters.remove(ROUND));
                this.attribCount = Integer.parseInt(this.parameters.remove(ATTRIB_COUNT));
                this.classAttribNum = Integer.parseInt(this.parameters.remove(CLASS_ATTR_NO));
            }
            catch (NumberFormatException e) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameters " + ROUND 
                        + ", " + ATTRIB_COUNT + " and " + CLASS_ATTR_NO + " must be present and numeric.");
            }
        }

        // limit the task by number of attributes (if known)
        if (this.attribCount > 0 && this.end > this.attribCount) {
            this.end = this.attribCount;
        }
        // check round constraints (round must be within [start, end + 1])
        if (this.start > this.end || this.start <= 0 || this.end <= 0
                || this.round < this.start || this.round > this.end + 1) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Round number constraints violated.");
        }
    }


    @Override
    public void perform() throws TaskException {

        this.setExpandedId();

        try {
            // select the best attribute
            if (this.round > this.start){

                String [] evalFiles = new String [this.input.size()/2 - 1];
                int best;

                for (int i = 0; i < this.input.size() - 2; i += 2){
                    evalFiles[i/2] = this.input.get(i);
                }

                // select the best settings and find out if this is the last round
                best = this.evalRound(evalFiles);

                if (best >= 0){
                    Logger.getInstance().message(this.id + ": best result: " + best
                            + " - " + this.measure + " : " + this.lastBest
                            + " with attributes " + this.lastBestAttributesList, Logger.V_INFO);
                }

                // for the final round, write the best list of attributes
                if (this.round > this.end){
                    this.writeBestStats(this.output.get(2), best);
                }

                // copy the best settings to the destination location (final or temporary)
                if (best < 0){
                    Logger.getInstance().message(this.id +
                            ": copying the best results from previous round to the final destination.", Logger.V_INFO);
                    FileUtils.copyFile(this.getTempfileName(TempfileTypes.BEST_STATS, this.round - 2, 0), this.output.get(1));
                    FileUtils.copyFile(this.getTempfileName(TempfileTypes.BEST_CLASSIF, this.round - 2, 0), this.output.get(0));
                }
                else if (this.round > this.end){
                    Logger.getInstance().message(this.id +
                            ": this is the final round. Copying files to their final destination.", Logger.V_INFO);
                    FileUtils.copyFile(this.input.get(best*2), this.output.get(1));
                    FileUtils.copyFile(this.input.get(best*2 + 1), this.output.get(0));
                }
                else {
                    FileUtils.copyFile(this.input.get(best*2),
                            this.getTempfileName(TempfileTypes.BEST_STATS, this.round - 1, 0));
                    FileUtils.copyFile(this.input.get(best*2 + 1),
                            this.getTempfileName(TempfileTypes.BEST_CLASSIF, this.round - 1, 0));
                }

                if (this.deleteTempfiles){
                    this.deleteTempfiles();
                }
            }
            // create the next round
            if (this.round <= this.end){
                
               Hashtable<String, String> [] paramSets = this.round == this.start
                       ? this.prepareFirstParams() : this.prepareRoundParams();
               String idBase = this.round == this.start  // prevent the "#"-like ids from nesting
                       ? this.id : this.id.substring(0, this.id.lastIndexOf("#"));
               Vector<TaskDescription> subTasks = this.createNextRound(idBase, paramSets);

               Logger.getInstance().message(this.id + ": assigning tasks for the next round ...", Logger.V_INFO);
               Plan.getInstance().appendToTask(this.id, subTasks);
            }
        }
        catch (TaskException te){
            throw te;
        }
        catch (Exception e){
            Logger.getInstance().logStackTrace(e, Logger.V_IMPORTANT);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }


    /**
     * This finds the best result from the last round and stores its value and the attributes
     * used to achieve it, then checks if the improvement was big enough for the algorithm to
     * continue.
     *
     * @param evalFiles the statistics files from the last round
     * @return the number of the best setting, or -1 if all are worse than previous
     */
    private int evalRound(String [] evalFiles) throws IOException, Exception {

        RandomAccessFile lastRoundStats = new RandomAccessFile(this.getTempfileName(TempfileTypes.ROUND_STATS,
                this.round - 1, 0), "rw");

        // find out which trial of the previous round gave the best results
        int [] order = this.getAttributeOrder();
        Pair<Integer, Double> best = this.selectBest(evalFiles, order);

        // find the best value of the round BEFORE the previous one
        String lastBestInfo = lastRoundStats.readLine();
        this.lastBest = Double.parseDouble(lastBestInfo.split(":")[1].trim());

        for (int i = 0; i < best.first; ++i){
            lastRoundStats.readLine();
        }

        // store the current best attributes
        this.lastBestAttributesList = lastRoundStats.readLine();
        this.createMask(this.lastBestAttributesList.split("\\s+"));

        // test if the previous round improved the results
        if (best.second < this.lastBest + this.minImprovement){

            if (best.second >= this.lastBest){
                Logger.getInstance().message(this.id + " : convergency criterion met.", Logger.V_INFO);
            }
            else { // worse than the previous round -- will revert to last results
                Logger.getInstance().message(this.id + " : worse than previous round, reverting.", Logger.V_INFO);
                best.first = -1;
            }
            this.end = this.round - 1;
        }
        // store the last best value
        if (best.second >= this.lastBest){
            this.lastBest = best.second;
        }

        // write down the selected option
        lastRoundStats.seek(lastRoundStats.length());
        if (best.first == -1){
            lastRoundStats.write(("Best " + best.second + " worse than previous, reverting." + LF).getBytes());
        }
        else {
            lastRoundStats.write(("Selected: " + best.first + " with " + this.measure + " of " + best.second + LF).getBytes());
            lastRoundStats.write((this.getLastBestNames() + LF).getBytes());
        }
        lastRoundStats.close();
        
        return best.first;
    }

    /**
     * This creates a bit mask from the list of used attributes. The class attribute number
     * is always true in the mask (since this attribute is always used).
     *
     * @param attribs the list of used attributes numbers
     */
    private void createMask(String [] attribs) {

        this.lastBestAttributes = new boolean [this.attribCount + 1];

        for (int i = 0; i < attribs.length; ++i){
            int attribNo = Integer.parseInt(attribs[i]);
            this.lastBestAttributes[attribNo] = true;
        }

        this.lastBestAttributes[this.classAttribNum] = true;
    }


    /**
     * This creates all the {@link TaskDescription}s that are needed for the next round
     * of tests (just tries to add all the remaining attributes, one at a time). The last
     * task is the evaluation and the start of the next round of the algorithm (or the
     * final evaluation).
     *
     * @param idBase the beginning of the ID for all the next tasks
     * @param paramSets all the parameter sets that will be used in the classification tasks in the next round
     * @return the list of all needed tasks
     */
    private Vector<TaskDescription> createNextRound(String idBase, Hashtable<String, String> [] paramSets) throws TaskException {

        Vector<TaskDescription> newTasks = new Vector<TaskDescription>();     
        Vector<String> nextRoundInput = new Vector<String>();
        Hashtable<String, String> nextRoundParams = new Hashtable<String, String>();
        TaskDescription nextRoundTask;        

        // create the classifier tasks and evaluation tasks that are connected to the given parameter sets
        for (int i = 0; i < paramSets.length; ++i){

            TaskDescription classifTask, evalTask;
            Vector<String> classifInput = new Vector<String>(2), classifOutput = new Vector<String>(1),
                    evalInput = new Vector<String>(2), evalOutput = new Vector<String>(1);
            Hashtable<String, String> evalParams = new Hashtable<String, String>();

            // set all parameters, inputs and outputs
            classifInput.addAll(this.input.subList(this.input.size() - 2, this.input.size()));
            classifOutput.add(this.getTempfileName(TempfileTypes.CLASSIF, this.round, i));

            evalParams.put(CLASS_ARG, this.classArg);
            evalInput.add(this.input.get(this.input.size() - 1));
            evalInput.add(classifOutput.get(0));
            evalOutput.add(this.getTempfileName(TempfileTypes.STATS, this.round, i));

            nextRoundInput.add(evalOutput.get(0));
            nextRoundInput.add(classifOutput.get(0));

            // create the tasks
            classifTask = new TaskDescription(idBase + "#classif" + this.round + "-" + i,
                    WekaClassifier.class.getName(), paramSets[i], classifInput, classifOutput);
            evalTask = new TaskDescription(idBase + "#eval" + this.round + "-" + i,
                    EvalClassification.class.getName(), evalParams, evalInput, evalOutput);

            // set their dependencies
            classifTask.setStatus(TaskStatus.WAITING);
            evalTask.setDependency(classifTask);

            // add them to the list
            newTasks.add(classifTask);
            newTasks.add(evalTask);
        }

        // create the task for the next round
        nextRoundParams.put(MEASURE, this.measure);
        nextRoundParams.put(START, Integer.toString(this.start));
        nextRoundParams.put(END, Integer.toString(this.end));
        nextRoundParams.put(CLASS_ARG, this.classArg);
        nextRoundParams.put(WEKA_CLASS, this.wekaClass);
        nextRoundParams.put(MIN_IMPROVEMENT, Double.toString(this.minImprovement));
        nextRoundParams.put(ROUND, Integer.toString(this.round + 1));
        nextRoundParams.put(ATTRIB_COUNT, Integer.toString(this.attribCount));
        nextRoundParams.put(CLASS_ATTR_NO, Integer.toString(this.classAttribNum));
        nextRoundParams.put(TEMPFILE, this.tempFilePattern);
        nextRoundParams.put(DELETE_TEMPFILES, Boolean.toString(this.deleteTempfiles));
        
        nextRoundInput.addAll(this.input.subList(this.input.size() - 2, this.input.size()));

        nextRoundTask = new TaskDescription(idBase + (this.round < this.end ? "#round" + (this.round + 1) : "#finalize"),
                GreedyAttributeSearch.class.getName(),
                nextRoundParams, nextRoundInput, (Vector<String>) this.output.clone());

        // set its dependencies
        for (TaskDescription t : newTasks){
            nextRoundTask.setDependency(t);
        }
        newTasks.add(nextRoundTask);

        return newTasks;
    }

    /**
     * Prepares the parameters for the current round trials, using the last round parameters
     * plus one additional at a time. Marks all the selections in the round statistics file.
     *
     * @return the sets of parameters for the current round
     */
    private Hashtable<String, String> [] prepareRoundParams() throws IOException {

        Vector<Hashtable<String, String>> paramSets = new Vector<Hashtable<String, String>>();
        PrintStream roundStatsFile = new PrintStream(this.getTempfileName(TempfileTypes.ROUND_STATS, this.round, 0));

        roundStatsFile.println("Last best:" + this.lastBest);

        // prepare the parameter sets for the individual trials of this round
        for (int i = 0; i < this.lastBestAttributes.length; ++i) {
            if (!this.lastBestAttributes[i]) {
                paramSets.add(this.prepareParamSet(this.lastBestAttributesList + " " + i));

                roundStatsFile.println(this.lastBestAttributesList + " " + i);
            }
        }

        roundStatsFile.close();
        return paramSets.toArray(new Hashtable [0]);
    }


    /**
     * Prepares the parameters for the first round, attribList.e. all n-tuples of parameters. Marks the
     * order in the round statistics file.
     *
     * @return sets of parameters for the first round
     */
    private Hashtable<String, String> [] prepareFirstParams() throws Exception {

        Vector<Hashtable<String, String>> paramSets = new Vector<Hashtable<String, String>>();
        PrintStream roundStatsFile = new PrintStream(this.getTempfileName(TempfileTypes.ROUND_STATS, this.round, 0));
        Vector<String> combinations;
        String startAttribs = this.getStartAttributes();

        if (startAttribs != null){
            combinations = new Vector<String>(1);
            combinations.add(startAttribs);
        }
        else {
            combinations = MathUtils.combinations(this.round, this.attribCount);
        }

        roundStatsFile.println("Last best:" + 0);

        for (int i = 0; i < combinations.size(); ++i){

            String combination = this.moveAttribs(combinations.get(i));

            paramSets.add(this.prepareParamSet(combination));
            roundStatsFile.println(combination);
        }
        
        roundStatsFile.close();
        return paramSets.toArray(new Hashtable [0]);
    }


    /**
     * This finds out the number of available arguments and the order of the class argument
     * and saves it to {@link #classAttribNum} and {@link #attribCount} and select the start
     * attributes by number, if the {@link #startAttrib} member is set.
     *
     * @return the space-separated list of starting attributes, or null if their names haven't been set-up \
     *      in the parameters of this {@link Task}
     */
    private String getStartAttributes() throws Exception {

        Instances train = FileUtils.readArff(this.input.get(0));

        if (train.attribute(this.classArg) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Couldn't find the class attribute "
                    + this.classArg + " in the training data file.");
        }

        this.attribCount = train.numAttributes() - 1;
        this.classAttribNum = train.attribute(this.classArg).index();

        if (this.startAttrib != null){

            StringBuilder startAttribNums = new StringBuilder();

            for (String attrib: this.startAttrib){

                if (train.attribute(attrib) == null){
                    throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                            "Couldn't find the starting attribute " + attrib + ".");
                }
                if (startAttribNums.length() != 0){
                    startAttribNums.append(" ");
                }
                startAttribNums.append(train.attribute(attrib).index());
            }

            return startAttribNums.toString();
        }
        return null;
    }

    /**
     * Moves all the attributes numbers within the string past the class attribute one to the right
     * (attribList.e\. adds one to them).
     * @param list list of attributes
     * @return
     */
    private String moveAttribs(String list) {

        String [] attribNums = list.split("\\s+");
        StringBuilder ret = new StringBuilder();

        for (String attribNum : attribNums){

            int n = Integer.parseInt(attribNum);

            if (ret.length() != 0){
                ret.append(" ");
            }
            if (n >= this.classAttribNum){
                ret.append(n+1);
            }
            else {
                ret.append(n);
            }
        }

        return ret.toString();
    }

    /**
     * This prepares the parameter set for a classification task with the given list of attributes to use.
     *
     * @param attribList the list of attributes to be used
     * @return the full parameter set for a classification task
     */
    private Hashtable<String, String> prepareParamSet(String attribList) {

        Hashtable<String, String> paramSet = new Hashtable<String, String>();

        paramSet.put(CLASS_ARG, this.classArg);
        paramSet.put(WEKA_CLASS, this.wekaClass);
        paramSet.put(TEMPFILE, this.tempFilePattern);
        paramSet.put(WekaClassifier.SELECT_ARGS, attribList);
        paramSet.putAll(this.parameters); // these are just the classifier parameters

        return paramSet;
    }

    /**
     * Returns the space-separated list of names of the {@link #lastBestAttributes}.
     * @return the space-separated list of names of the selected attributes
     */
    private String getLastBestNames() throws Exception {

        Instances structure = FileUtils.readArffStructure(this.input.get(this.input.size()-2), true);
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < this.lastBestAttributes.length; ++i){
            if (this.lastBestAttributes[i] && i != this.classAttribNum){
                if (text.length() > 0){
                    text.append(" ");
                }
                text.append(structure.attribute(i).name());
            }
        }

        return text.toString();
    }

    /**
     * This retrieves the name of the attribute order file from the list of inputs if the attrib_order
     * parameter is set.
     * @return the name of the attribute order file, or null
     */
    private String getAttributeOrderFile() {

        String ret = null;

        if (this.parameters.get(ATTRIB_ORDER) != null){
            ret = this.input.get(this.input.size()-1);
            this.input.remove(this.input.size()-1);
        }
        return ret;
    }

    /**
     * This prepares the correct order of consideration for the attributes that were tested in the last round.
     * Returns null if the attribute order file is not set-up, so that the order will be linear.
     *
     * @return the order for consideration of attributes
     * @throws IOException
     * @throws TaskException
     */
    private int [] getAttributeOrder() throws IOException, TaskException {

        if (this.attributeOrderFile == null){
            return null;
        }

        int [] order = new int [this.input.size()/2 - 1];
        int [] orderAll;
        int [] orderSel = new int [this.input.size()/2 -1];

        // read the selected attribute order from the stats file
        RandomAccessFile stats = new RandomAccessFile(this.getTempfileName(TempfileTypes.ROUND_STATS, this.round - 1, 0), "r");
        String line = stats.readLine(); // skip first line
        try {
            for (int i = 0; i < orderSel.length; ++i){
                line = stats.readLine();
                orderSel[i] = Integer.parseInt(line.substring(line.lastIndexOf(' ') + 1));
            }
        }
        catch (Exception e){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Invalid round stats file "
                    + this.round);
        }
        finally {
            stats.close();
        }

        // read all attributes' correct order from the attribute order file
        RandomAccessFile attrOrder = new RandomAccessFile(this.attributeOrderFile, "r");
        try {
            line = attrOrder.readLine();
            orderAll = StringUtils.readListOfInts(line);
        }
        catch (Exception e){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Invalid attribute order file.");
        }

        // create the final order
        for (int i = 0; i < orderAll.length; ++i){
            int index = -1;

            for (int j = 0; j < orderSel.length; ++i){
                if (orderSel[j] == orderAll[i]){
                    index = j;
                    break;
                }
            }
            if (index == -1){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Invalid attribute order file.");
            }
            order[i] = index;
        }
        return order;
    }

    /**
     * This writes just the list of the best attribute numbers.
     */
    @Override
    protected void writeBestStats(String outFile, int settingNo) throws IOException {

        PrintStream out = new PrintStream(outFile);
        String bestList = this.lastBestAttributesList;

        if (settingNo < 0){ // if the last round didn't improve the results, rip off one attribute
            bestList = bestList.substring(0, bestList.lastIndexOf(" "));
        }
        
        out.println(bestList);
        out.close();
    }

    /**
     * This deletes all the tempfiles of the individual trials, but keeps the round statistics.
     */
    @Override
    protected void deleteTempfiles() {

        for (int i = 0; i < this.input.size() - 2; i++) {
            FileUtils.deleteFile(this.input.get(i));
        }
    }


}
