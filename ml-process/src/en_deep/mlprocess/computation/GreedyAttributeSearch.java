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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;

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

    /** The name of the "start" parameter */
    private static final String START = "start";
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


    /* METHODS */

    /**
     * <p>
     * This constructs a {@link GreedyAttributeSearch} object. The constructor just checks
     * the validity of parameters, inputs and outputs.
     * </p>
     * <p>
     * There are the same parameters as in {@link WekaClassifier}, plus two that are same
     * as in {@link SettingSelector} -- <tt>measure</tt> and <tt>tempfile</tt>.
     * There are additional compulsory parameters:
     * </p>
     * <ul>
     * <li><tt>start</tt> -- the starting number of attributes (should be very small, for all
     * possible combinations will be tried)</li>
     * <li><tt>end</tt>The maximum number of attributes that are to be tried</li>
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
     * There is a special parameter reserved for the program (which controls the process). If the task
     * is run with this parameter set higher than start, more inputs are allowed:
     * <ul>
     * <li><tt>round_number</tt> -- the current number of attributes used
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

        // check all parameters related to the round of the task
        if (this.parameters.get(START) == null || this.parameters.get(END) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }
        try {
            this.start = Integer.valueOf(this.parameters.get(START));
            this.end = Integer.valueOf(this.parameters.get(END));
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }

        if (this.parameters.get(ROUND) == null){
            this.round = this.start;
        }
        else {
            try {
                this.round = Integer.valueOf(this.parameters.get(ROUND));
            }
            catch (NumberFormatException e){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
            }
        }
        if (this.start > this.end || this.start <= 0 || this.end <= 0
                || this.round < this.start || this.round > this.end){
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
        if (this.round == this.end){
            if (this.parameters.get(MEASURE) == null){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
            }
            this.measure = this.parameters.remove(MEASURE);
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
        this.tempFilePattern = Process.getInstance().getWorkDir() + this.parameters.remove(TEMPFILE);

        // check the optional parameter
        if (this.parameters.get(MIN_IMPROVEMENT) != null){
            try {
                this.minImprovement = Integer.valueOf(this.parameters.get(MIN_IMPROVEMENT));
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

                Vector<String> evalFiles = new Vector<String>(this.input.size()/2);
                int best;

                for (int i = 0; i < this.input.size(); i += 2){
                    evalFiles.add(this.input.get(i));
                }

                // select the best settings and find out if this is the last round
                best = this.evalRound(evalFiles);

                // copy the best settings to the destination location
                if (this.round == this.end){
                    FileUtils.copyFile(this.input.get(best*2), this.output.get(1));
                    FileUtils.copyFile(this.input.get(best*2 + 1), this.output.get(0));
                }
                else {
                    FileUtils.copyFile(this.input.get(best*2),
                            this.tempFilePattern.replace("*", "(" + round + "-best)") + STATS_EXT);
                    FileUtils.copyFile(this.input.get(best*2 + 1),
                            this.tempFilePattern.replace("*", "(" + round + "-best)") + CLASS_EXT);
                }
            }
            // create the next round
            if (this.round < this.end){
               Vector<TaskDescription> subTasks = this.createNextRound();

               Plan.getInstance().appendToTask(this.id, subTasks);
            }
        }
        catch (TaskException te){
            throw te;
        }
        catch (Exception e){
            Logger.getInstance().message(e.getMessage(), Logger.V_IMPORTANT);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id);
        }
    }


    /**
     * TODO najde nejlepší výsledek, zkontroluje splnění zlepšovací podmínky (nastaví round = end?) a uloží
     * do členské proměnné vítěznou množinu atributů.
     */
    private int evalRound(Vector<String> evalFiles) throws IOException {

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

        return bestIndex;
    }

    /**
     * This creates all the {@link TaskDescription}s that are needed to test the classifier under
     * the given conditions. The last task is the evaluation mode of this class itself.
     *
     * TODO rewrite for next round
     * @return the list of all needed tasks
     */
    private Vector<TaskDescription> createMeasuringTasks() throws TaskException {

        Vector<TaskDescription> newTasks = new Vector<TaskDescription>();
        Hashtable<String, String> [] paramSets = null;
        Vector<String> lastTaskInput = new Vector<String>();
        Hashtable<String, String> lastTaskParams = new Hashtable<String, String>();
        TaskDescription lastTask;

        // differentiate the needed sets of classifier parameters
        for (String paramName : this.parameters.keySet()){

            String [] paramVals = this.parameters.get(paramName).split("\\s+");

            if (paramSets == null){
                paramSets = new Hashtable[paramVals.length];
            }
            if (paramVals.length != paramSets.length){ // different numbers of parameters
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
            }
            for (int i = 0; i < paramSets.length; ++i){

                if (paramSets[i] == null){
                    paramSets[i] = new Hashtable<String, String>(this.parameters.size());

                    paramSets[i].put(CLASS_ARG, this.classArg);
                    paramSets[i].put(WEKA_CLASS, this.wekaClass);
                }
                paramSets[i].put(paramName, paramVals[i]);
            }
        }

        // create the classifier tasks and evaluation tasks that are connected to them
        for (int i = 0; i < paramSets.length; ++i){

            TaskDescription classifTask, evalTask;
            Vector<String> classifOutput = new Vector<String>(1),
                    evalInput = new Vector<String>(2), evalOutput = new Vector<String>(1);
            Hashtable<String, String> evalParams = new Hashtable<String, String>();

            // set all parameters, inputs and outputs
            evalParams.put(CLASS_ARG, this.classArg);
            classifOutput.add(this.tempFilePattern.replace("*", "(" + i + ")") + CLASS_EXT);
            evalInput.add(this.input.get(1));
            evalInput.add(classifOutput.get(0));
            evalOutput.add(this.tempFilePattern.replace("*", "(" + i + ")") + STATS_EXT);
            lastTaskInput.add(evalOutput.get(0));
            lastTaskInput.add(classifOutput.get(0));

            // create the tasks
            classifTask = new TaskDescription(this.id + "#classif" + i, WekaClassifier.class.getName(),
                    paramSets[i], (Vector<String>) this.input.clone(), classifOutput);
            evalTask = new TaskDescription(this.id + "#eval" + i, EvalClassification.class.getName(),
                    evalParams, evalInput, evalOutput);

            // set their dependencies
            classifTask.setStatus(TaskStatus.WAITING);
            evalTask.setDependency(classifTask);

            // add them to the list
            newTasks.add(classifTask);
            newTasks.add(evalTask);
        }

        // create the last task that will select the best result
        lastTaskParams.put(MEASURE, this.measure);
        lastTaskParams.put(EVAL, "1");
        lastTask = new TaskDescription(this.id + "#select", GreedyAttributeSearch.class.getName(),
                lastTaskParams, lastTaskInput, (Vector<String>) this.output.clone());
        
        for (TaskDescription t : newTasks){
            lastTask.setDependency(t);
        }
        newTasks.add(lastTask);

        return newTasks;
    }

}
