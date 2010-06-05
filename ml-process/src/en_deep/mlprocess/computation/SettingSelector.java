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
 * This class tries to run the given WEKA classifier on the same data with several
 * different sets of settings and then selects the one with the best results.
 *
 * @author Ondrej Dusek
 */
public class SettingSelector extends Task {

    /* CONSTANTS */

    /** The name of the "measure" parameter */
    static final String MEASURE = "measure";

    /** The name of the reserved "eval" parameter */
    private static final String EVAL = "select_from_evaluations";

    /** The name of the "tempfile" parameter */
    static final String TEMPFILE = "tempfile";

    /** The name of the "class_arg" parameter */
    private static final String CLASS_ARG = WekaClassifier.CLASS_ARG;
    /** The name of the "weka_class" parameter */
    private static final String WEKA_CLASS = WekaClassifier.WEKA_CLASS;

    /** File extension for classification tempfiles */
    static final String CLASS_EXT = ".arff";
    /** Extension for statistics tempfiles */
    static final String STATS_EXT = ".txt";

    /* DATA */

    /** The name of the WEKA algorithm to be used */
    private String wekaClass;

    /** The name of the class attribute to be computed and evaluated upon */
    private String classArg;

    /** The name of the used statistical measure to compare the results */
    private String measure;

    /** The temporary files pattern */
    private String tempFilePattern;

    /** Are we running in the evaluation mode ? */
    private boolean evalMode;

    /* METHODS */

    /**
     * <p>
     * This constructs a {@link SettingSelector} object. The constructor just checks
     * the validity of parameters, inputs and outputs.
     * </p>
     * <p>
     * There are the same parameters as in {@link WekaClassifier}, except that the individual
     * classifier parameters are now space-separated lists of possible values (the number of
     * values must be the same). There are additional compulsory parameters:
     * </p>
     * <ul>
     * <li><tt>measure</tt> -- the measure selected for comparing the results</li>
     * <li><tt>tempfile</tt> -- the temporary files pattern (must contain one asterisk), without
     * the file extension (automatically adds ".arff" for data and ".txt" for statistics)</li>
     * </ul>
     * <p>
     * There must be exactly two inputs (first of which is the training data and second the
     * testing data) and two outputs (one is for the classification output and one for
     * the output of the best set of parameters).
     * </p>
     * There is a special parameter reserved for the program (the process ends with this
     * parameter). If the task is run with this parameter, more inputs are allowed.
     * <ul>
     * <li><tt>eval</tt> -- starts the selection from finished evaluations, if it's set
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    public SettingSelector(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check the number of inputs and outputs
        if (this.parameters.get(EVAL) == null && input.size() != 2 || input.size() < 2 || input.size() % 2 != 0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (output.size() != 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }

        // check the compulsory parameters for the evaluation case
        if (this.parameters.get(EVAL) != null){
            if (this.parameters.get(MEASURE) == null){
                Logger.getInstance().message(this.id + " : no measure specified.", Logger.V_IMPORTANT);
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
            }
            this.evalMode = true;
            this.measure = this.parameters.get(MEASURE);

            return;
        }

        // check the compulsory parameters for the normal case and save them
        if (this.parameters.get(WEKA_CLASS) == null || this.parameters.get(CLASS_ARG) == null
                || this.parameters.get(MEASURE) == null || this.parameters.get(TEMPFILE) == null
                || this.parameters.get(TEMPFILE).indexOf("*") == -1){
            Logger.getInstance().message(this.id + " : some parameters are missing.", Logger.V_IMPORTANT);
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }
        this.classArg = this.parameters.remove(CLASS_ARG);
        this.measure = this.parameters.remove(MEASURE);
        this.wekaClass = this.parameters.remove(WEKA_CLASS);
        this.tempFilePattern = Process.getInstance().getWorkDir() + this.parameters.remove(TEMPFILE);
    }

    @Override
    public void perform() throws TaskException {

        try {
            // evaluation mode
            if (this.evalMode){

                Vector<String> evalFiles = new Vector<String>(this.input.size()/2);
                int best;

                Logger.getInstance().message(this.id + ": Selecting the best result ...", Logger.V_INFO);

                for (int i = 0; i < this.input.size(); i += 2){
                    evalFiles.add(this.input.get(i));
                }

                // select the best settings
                best = this.selectBest(evalFiles);

                Logger.getInstance().message(this.id + ": Best result: " + best, Logger.V_INFO);

                // copy the best settings to the destination location
                FileUtils.copyFile(this.input.get(best*2), this.output.get(1));
                FileUtils.copyFile(this.input.get(best*2 + 1), this.output.get(0));

            }
            // normal mode -- assign tasks for computation
            else {
               Vector<TaskDescription> subTasks = this.createMeasuringTasks();

               Logger.getInstance().message(this.id + ": assigning tasks for computation ...", Logger.V_INFO);
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
     * Opens the given files with evaluation statistics as output by
     * {@link en_deep.mlprocess.evaluation.EvalClassfication} and select the one which has the
     * best characteristics according to {@link #measure}.
     *
     * @param evalFiles a list of statistics files
     * @return the number of the best statistics within evalFiles
     */
    private int selectBest(Vector<String> evalFiles) throws IOException {

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
            if (paramVals.length != paramSets.length){
                Logger.getInstance().message(this.id + " : numbers of the individual parameters vary.", Logger.V_IMPORTANT);
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
        lastTask = new TaskDescription(this.id + "#select", SettingSelector.class.getName(),
                lastTaskParams, lastTaskInput, (Vector<String>) this.output.clone());
        
        for (TaskDescription t : newTasks){
            lastTask.setDependency(t);
        }
        newTasks.add(lastTask);

        return newTasks;
    }

}
