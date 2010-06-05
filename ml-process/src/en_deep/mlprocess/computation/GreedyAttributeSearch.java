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

import en_deep.mlprocess.Process;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Plan;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.TaskDescription;
import en_deep.mlprocess.TaskDescription.TaskStatus;
import en_deep.mlprocess.evaluation.EvalClassification;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.MathUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;


/**
 * This class applies the greedy attribute selection algorithm with the given
 * WEKA classifier type and settings on the given data.
 *
 * @author Ondrej Dusek
 */
public class GreedyAttributeSearch extends Task {

    /* CONSTANTS */

    /** The name of the "measure" parameter */
    private static final String MEASURE = SettingSelector.MEASURE;

    /** The name of the reserved "round" parameter */
    private static final String ROUND = "round_number";
    /** The name of the reserved "attrib_count" parameter */
    private static final String ATTRIB_COUNT = "attrib_count";
    /** The name of the reserved "class_attr_no" parameter */
    private static final String CLASS_ATTR_NO = "class_attr_no";

    /** The name of the "start" parameter */
    private static final String START = "start";
    /** The name of the "start_attrib" parameter */
    private static final String START_ATTRIB = "start_attrib";
    /** The name of the "end" parameter */
    private static final String END = "end";
    /** The minimal improvement that is possible */
    private static final String MIN_IMPROVEMENT = "min_improvement";

    /** The name of the "tempfile" parameter */
    private static final String TEMPFILE = SettingSelector.TEMPFILE;

    /** The name of the "class_arg" parameter */
    private static final String CLASS_ARG = WekaClassifier.CLASS_ARG;
    /** The name of the "weka_class" parameter */
    private static final String WEKA_CLASS = WekaClassifier.WEKA_CLASS;
    
    /** File extension for classification tempfiles */
    private static final String CLASS_EXT = SettingSelector.CLASS_EXT;
    /** Extension for statistics tempfiles */
    private static final String STATS_EXT = SettingSelector.STATS_EXT;

    /** File types used in the computation */
    private enum FileTypes {
        CLASSIF, STATS, ROUND_STATS, BEST_CLASSIF, BEST_STATS
    }

    /** CR/LF */
    private static final String LF = System.getProperty("line.separator");

    /* DATA */

    /** The name of the WEKA algorithm to be used */
    private String wekaClass;

    /** The name of the class attribute to be computed and evaluated upon */
    private String classArg;

    /** The name of the used statistical measure to compare the results */
    private String measure;

    /** The temporary files pattern */
    private String tempFilePattern;

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
    /** The part of the id that resulted from task expansions, needed for tempfiles */
    private String expandedId;

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
     * mutualy exclusive):
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
     * There is one more optional parameter, which defaults to 0:
     * </p>
     * <ul>
     * <li><tt>min_improvement</tt> -- the minimal required improvement in the selected measure for the
     * algorithm to continue</li>
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

        // set the expandedId parameter
        this.expandedId = this.id.indexOf('#') == -1 ? "" : this.id.substring(this.id.indexOf('#'));
        if (this.expandedId.substring(this.expandedId.lastIndexOf('#')).matches("#round[0-9]+")
                || this.expandedId.endsWith("#finalize")){
            this.expandedId = this.expandedId.substring(0, this.expandedId.lastIndexOf('#'));
        }

        // check all parameters related to the round of the task
        if ((this.parameters.get(START) == null && this.parameters.get(START_ATTRIB) == null)
                || (this.parameters.get(START) != null && this.parameters.get(START_ATTRIB) != null)
                || this.parameters.get(END) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }
        try {
            if (this.parameters.get(START) != null){
                this.start = Integer.parseInt(this.parameters.remove(START));
            }
            else {
                this.startAttrib = this.parameters.get(START_ATTRIB).split("\\s+");
                this.start = this.startAttrib.length;
            }
            this.end = Integer.parseInt(this.parameters.remove(END));
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }

        // first round
        if (this.parameters.get(ROUND) == null){
            this.round = this.start;
        }
        // other rounds -- need more paramters
        else {
            try {
                this.round = Integer.parseInt(this.parameters.remove(ROUND));
                this.attribCount = Integer.parseInt(this.parameters.remove(ATTRIB_COUNT));
                this.classAttribNum = Integer.parseInt(this.parameters.remove(CLASS_ATTR_NO));
            }
            catch (Exception e){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
            }
        }
        if (this.attribCount > 0 && this.end > this.attribCount){ // limit the task by number of attributes (if known)
            this.end = this.attribCount;
        }
        // check round constraints (round must be within [start, end + 1])
        if (this.start > this.end || this.start <= 0 || this.end <= 0
                || this.round < this.start || this.round > this.end + 1){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }

        // check the number of inputs and outputs
        if (this.round == this.start && input.size() != 2 || input.size() < 2 || input.size() % 2 != 0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (output.size() != 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }

        // final round -- do not check some parameters
        if (this.round > this.end){
            if (this.parameters.get(MEASURE) == null || this.parameters.get(TEMPFILE) == null){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
            }
            this.measure = this.parameters.remove(MEASURE);
            this.tempFilePattern = this.parameters.remove(TEMPFILE);
            return;
        }

        // check the compulsory parameters for the normal case and save them
        if (this.parameters.get(WEKA_CLASS) == null || this.parameters.get(CLASS_ARG) == null
                || this.parameters.get(MEASURE) == null || this.parameters.get(TEMPFILE) == null
                || this.parameters.get(TEMPFILE).indexOf("*") == -1){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }
        this.classArg = this.parameters.remove(CLASS_ARG);
        this.measure = this.parameters.remove(MEASURE);
        this.wekaClass = this.parameters.remove(WEKA_CLASS);
        this.tempFilePattern = this.parameters.remove(TEMPFILE);

        // check the optional parameter
        if (this.parameters.get(MIN_IMPROVEMENT) != null){
            try {
                this.minImprovement = Double.parseDouble(this.parameters.remove(MIN_IMPROVEMENT));
            }
            catch(NumberFormatException e){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
            }
        }
    }

    @Override
    public void perform() throws TaskException {

        try {
            // select the best attribute
            if (this.round > this.start){

                Vector<String> evalFiles = new Vector<String>(this.input.size()/2 - 1);
                int best;

                for (int i = 0; i < this.input.size() - 2; i += 2){
                    evalFiles.add(this.input.get(i));
                }

                // select the best settings and find out if this is the last round
                best = this.evalRound(evalFiles);

                if (best >= 0){
                    Logger.getInstance().message(this.id + ": best result: " + best
                            + " - " + this.measure + " : " + this.lastBest
                            + " with attributes " + this.lastBestAttributesList, Logger.V_INFO);
                }

                // copy the best settings to the destination location (final or temporary)
                if (best < 0){
                    Logger.getInstance().message(this.id +
                            ": copying the best results from previous round to the final destination.", Logger.V_INFO);
                    FileUtils.copyFile(this.getFileName(FileTypes.BEST_STATS, this.round - 2, 0), this.output.get(1));
                    FileUtils.copyFile(this.getFileName(FileTypes.BEST_CLASSIF, this.round - 2, 0), this.output.get(0));
                }
                else if (this.round > this.end){
                    Logger.getInstance().message(this.id +
                            ": this is the final round. Copying files to their final destination.", Logger.V_INFO);
                    FileUtils.copyFile(this.input.get(best*2), this.output.get(1));
                    FileUtils.copyFile(this.input.get(best*2 + 1), this.output.get(0));
                }
                else {
                    FileUtils.copyFile(this.input.get(best*2),
                            this.getFileName(FileTypes.BEST_STATS, this.round - 1, 0));
                    FileUtils.copyFile(this.input.get(best*2 + 1),
                            this.getFileName(FileTypes.BEST_CLASSIF, this.round - 1, 0));
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
            e.printStackTrace();
            Logger.getInstance().message(e.getMessage(), Logger.V_IMPORTANT);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id);
        }
    }


    /**
     * This finds the best result from the last round and stores its value and the attributes
     * used to achieveit, then checks if the improvement was big enough for the algorithm to
     * continue.
     *
     * @param evalFiles the statistics files from the last round
     */
    private int evalRound(Vector<String> evalFiles) throws IOException {

        RandomAccessFile lastRoundStats = new RandomAccessFile(this.getFileName(FileTypes.ROUND_STATS, 
                this.round - 1, 0), "rw");
        String lastBestInfo = lastRoundStats.readLine();        
        int bestIndex = -1;
        double bestVal = -1.0;

        for (int i = 0; i < evalFiles.size(); ++i){

            RandomAccessFile stats = new RandomAccessFile(evalFiles.get(i), "r");
            String line = stats.readLine();

            while (line != null){
                String [] args = line.split(":");

                args[0] = args[0].trim();
                args[1] = args[1].trim();

                if (args[0].equalsIgnoreCase(this.measure)){

                    double val = Double.valueOf(args[1]);

                    if (val > bestVal){
                        bestIndex = i;
                        bestVal = val;
                    }
                    break;
                }

                line = stats.readLine();
            }
            stats.close();
        }

        // find the best value of the round BEFORE the previous one
        this.lastBest = Double.parseDouble(lastBestInfo.split(":")[1].trim());

        for (int i = 0; i < bestIndex; ++i){
            lastRoundStats.readLine();
        }

        // store the current best attributes
        this.lastBestAttributesList = lastRoundStats.readLine();
        this.createMask(this.lastBestAttributesList.split("\\s+"));

        // test if the previous round improved the results
        if (bestVal < this.lastBest + this.minImprovement){

            if (bestVal >= this.lastBest){
                Logger.getInstance().message(this.id + " : convergency criterion met.", Logger.V_INFO);
            }
            else { // worse than the previous round -- will revert to last results
                Logger.getInstance().message(this.id + " : worse than previous round, reverting.", Logger.V_INFO);
                bestIndex = -1;
            }
            this.end = this.round - 1;
        }
        // store the last best value
        if (bestVal >= this.lastBest){
            this.lastBest = bestVal;
        }

        // write down the selected option
        lastRoundStats.seek(lastRoundStats.length());
        lastRoundStats.write(("Selected: " + bestIndex + " with " + this.measure + " of " + bestVal + LF).getBytes());
        lastRoundStats.close();
        
        return bestIndex;
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
            classifOutput.add(this.getFileName(FileTypes.CLASSIF, this.round, i));

            evalParams.put(CLASS_ARG, this.classArg);
            evalInput.add(this.input.get(this.input.size() - 1));
            evalInput.add(classifOutput.get(0));
            evalOutput.add(this.getFileName(FileTypes.STATS, this.round, i));

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
     * Creates a file name out of the {@link #tempFilePattern} and the given
     * {@link GreedyAttributeSearch.FileTypes type}.
     *
     * @param type the type of the file
     * @param round the round for which the file is ment
     * @param order the number of the file (not used for ROUND_STATS, BEST_STATS and BEST_CLASSIF)
     * @return the file name
     */
    private String getFileName(FileTypes type, int round, int order){

        switch (type){
            case CLASSIF:
                return Process.getInstance().getWorkDir()
                        + this.tempFilePattern.replace("*", this.expandedId + "(" + round + "-" + order + ")") + CLASS_EXT;
            case STATS:
                return Process.getInstance().getWorkDir()
                        + this.tempFilePattern.replace("*", this.expandedId + "(" + round + "-" + order + ")") + STATS_EXT;
            case ROUND_STATS:
                return Process.getInstance().getWorkDir()
                        + this.tempFilePattern.replace("*", this.expandedId + "(" + round + "-stats)") + STATS_EXT;
            case BEST_STATS:
                return Process.getInstance().getWorkDir()
                        + this.tempFilePattern.replace("*", this.expandedId + "(" + round + "-best)") + STATS_EXT;
            case BEST_CLASSIF:
                return Process.getInstance().getWorkDir()
                        + this.tempFilePattern.replace("*", this.expandedId + "(" + round + "-best)") + CLASS_EXT;
            default:
                return "";
        }
    }

    /**
     * Prepares the parameters for the current round trials, using the last round parameters
     * plus one additional at a time. Marks all the selections in the round statistics file.
     *
     * @return the sets of parameters for the current round
     */
    private Hashtable<String, String> [] prepareRoundParams() throws IOException {

        Vector<Hashtable<String, String>> paramSets = new Vector<Hashtable<String, String>>();
        PrintStream roundStatsFile = new PrintStream(this.getFileName(FileTypes.ROUND_STATS, this.round, 0));

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
        PrintStream roundStatsFile = new PrintStream(this.getFileName(FileTypes.ROUND_STATS, this.round, 0));
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

        ConverterUtils.DataSource dataIn = new ConverterUtils.DataSource(this.input.get(0));
        Instances train = dataIn.getStructure();
        dataIn.reset();

        if (train.attribute(this.classArg) == null){
            Logger.getInstance().message(this.id + ": couldn't find the class attribute " + this.classArg +
                    " in the training data file.", Logger.V_IMPORTANT);
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }

        this.attribCount = train.numAttributes() - 1;
        this.classAttribNum = train.attribute(this.classArg).index();

        if (this.startAttrib != null){

            StringBuilder startAttribNums = new StringBuilder();

            for (String attrib: this.startAttrib){

                if (train.attribute(attrib) == null){
                    Logger.getInstance().message(this.id + ": couldn't find the startin attribute " + attrib,
                            Logger.V_IMPORTANT);
                    throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
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
}
