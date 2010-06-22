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
import en_deep.mlprocess.Plan;
import en_deep.mlprocess.TaskDescription.TaskStatus;
import en_deep.mlprocess.evaluation.EvalClassification;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.TaskDescription;
import en_deep.mlprocess.utils.FileUtils;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class unites some functions for complicated WEKA classification tasks that try to classify the same file
 * with different settings and select the best result.
 * @author Ondrej Dusek
 */
public abstract class WekaSettingTrials extends EvalSelector {

    /* CONSTANTS */
    
    /** The name of the reserved "eval" parameter */
    static final String EVAL = "select_from_evaluations";
    
    /* DATA */

    /**
     * The name of the class attribute to be computed and evaluated upon
     */
    protected String classArg;
    /** Are we running in the evaluation mode ? */
    protected boolean evalMode;
    /** The name of the WEKA algorithm to be used */
    protected String wekaClass;

    /* METHODS */

    /**
     * This initializes the task for the case of initial sub-task division or for the evaluation. It just
     * checks the compulsory parameters:
     * <ul>
     * <li><tt>class_arg<tt> -- the class attribute used for evaluation</li>
     * <li><tt>measure</tt> -- the measure of evaluation to be used for comparison</li>
     * <li><tt>weka_class</tt> -- the WEKA classifier to use</li>
     * <li><tt>tempfile</tt> -- the tempfile pattern to be used</li>
     * </ul>
     * It removes all the compulsory parameters from the parameter set so that only the WEKA classifier parameters
     * remain there.
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    public WekaSettingTrials(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check the compulsory parameters for the evaluation case
        if (this.parameters.get(EVAL) != null){
            this.evalMode = true;
            return;
        }

        // check the compulsory parameters for the normal case and save them
        if (this.parameters.get(WEKA_CLASS) == null || this.parameters.get(CLASS_ARG) == null
                || this.parameters.get(TEMPFILE) == null || this.parameters.get(TEMPFILE).indexOf("*") == -1){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Some parameters are missing.");
        }
        this.classArg = this.parameters.remove(CLASS_ARG);
        this.wekaClass = this.parameters.remove(WEKA_CLASS);
        this.tempFilePattern = this.parameters.remove(TEMPFILE);
    }

    @Override
    public void perform() throws TaskException {
        try {
            // evaluation mode
            if (this.evalMode) {
                String[] evalFiles = new String[this.input.size() / 2];
                int best;

                Logger.getInstance().message(this.id + ": Selecting the best result ...", Logger.V_INFO);

                for (int i = 0; i < this.input.size(); i += 2) {
                    evalFiles[i / 2] = this.input.get(i);
                }
                best = this.selectBest(evalFiles, null).first;

                Logger.getInstance().message(this.id + ": Best result: " + best, Logger.V_INFO);
                
                FileUtils.copyFile(this.input.get(best * 2), this.output.get(1));
                FileUtils.copyFile(this.input.get(best * 2 + 1), this.output.get(0));
            } 
            // normal mode
            else {
                Vector<TaskDescription> subTasks = this.createTasks();
                Logger.getInstance().message(this.id + ": assigning tasks for computation ...", Logger.V_INFO);
                Plan.getInstance().appendToTask(this.id, subTasks);
            }
        }
        catch (TaskException te) {
            throw te;
        }
        catch (Exception e) {
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

    /**
     * This creates all the {@link TaskDescription}s that are needed to test the classifier under
     * the given conditions. The last task is the evaluation mode of this class itself.
     *
     * @return the list of all needed tasks
     */
    protected Vector<TaskDescription> createTasks() throws TaskException {

        Vector<TaskDescription> newTasks = new Vector<TaskDescription>();
        Vector<String> lastTaskInput = new Vector<String>();
        Hashtable<String, String> lastTaskParams = new Hashtable<String, String>();
        TaskDescription lastTask;
        Hashtable<String, String>[] paramSets = this.prepareParamSets();

        // create the classifier tasks and evaluation tasks that are connected to them
        for (int i = 0; i < paramSets.length; ++i) {

            TaskDescription classifTask;
            TaskDescription evalTask;
            Vector<String> classifOutput = new Vector<String>(1);
            Vector<String> evalInput = new Vector<String>(2);
            Vector<String> evalOutput = new Vector<String>(1);
            Hashtable<String, String> evalParams = new Hashtable<String, String>();

            // set all parameters, inputs and outputs
            evalParams.put(CLASS_ARG, this.classArg);
            classifOutput.add(this.getTempfileName(TempfileTypes.CLASSIF, i));
            evalInput.add(this.input.get(1));
            evalInput.add(classifOutput.get(0));
            evalOutput.add(this.getTempfileName(TempfileTypes.STATS, i));
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
        lastTaskParams.put(WekaSettingTrials.EVAL, "1");
        lastTask = new TaskDescription(this.id + "#select", this.getClass().getName(), lastTaskParams,
                lastTaskInput, (Vector<String>) this.output.clone());
        for (TaskDescription t : newTasks) {
            lastTask.setDependency(t);
        }
        newTasks.add(lastTask);

        return newTasks;
    }

    /**
     * This prepares the individual parameter sets for the different classifier settings.
     * @return a list of parameters sets for the classification
     */
    protected abstract Hashtable<String, String> [] prepareParamSets() throws TaskException;

}
