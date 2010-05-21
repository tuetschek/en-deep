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

package en_deep.mlprocess;

import en_deep.mlprocess.exception.DataException;
import en_deep.mlprocess.exception.TaskException;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Vector;
import java.util.Hashtable;


/**
 * This class expands the wildcard characters in inputs and outputs specifications, possibly creating some
 * more tasks. This affects also the depending tasks in some cases. All the created tasks are put
 * in place of the original tasks. The first task to be processed is returned.
 * <p>
 * All the input "*" characters are expanded and single "*"s in output specifications, too. Only the
 * output "**" characters are not expanded, as the final file names and count are determined
 * by the task itself.
 * </p>
 * <p>
 * Expansion modes (only one pattern per file allowed):
 * </p>
 * <ul>
 * <li>input "*" - all usages must expand correspondingly</li>
 * <li>input "**" - all files just added to inputs of the given task</li>
 * <li>input "***" - carthesian product of tasks is created for each possible combination</li>
 * <li>output "*" - expanded corresponding to the input "*"s, trasitive to depending tasks</li>
 * </ul>
 *
 */
public class TaskExpander {


    /* DATA */

    /** The task to be processed */
    private TaskDescription task;

    /** Positions in the task output listings, where are wildcard patterns to be found */
    private Vector<Integer> inputTrans, inputHere, inputCarth;
    /** Positions in the task input listings, where are wildcard patterns to be found */
    private Vector<Integer> outputTrans;

    /** Expansion pattern matches for all affected tasks */
    private Hashtable<TaskDescription, Vector<TaskDescription>> expansions;


    /* METHODS */

    /**
     * This creates a new {@link TaskExpander} object, just setting the {@link TaskDescription} to be
     * processed. The expansion process is triggered by {@link TaskExpander#expand()}
     *
     * @param task the task to be expanded
     */
    public TaskExpander(TaskDescription task){

        this.task = task;
        this.expansions = new Hashtable<TaskDescription, Vector<TaskDescription>>();

        this.inputTrans = null;
        this.inputHere = null;
        this.inputCarth = null;

        this.outputTrans = null;
    }


    /**
     * The actual expansion process. When it's finished, the new tasks and tasks to be deleted may be
     * retrieved using {@link TaskExpander#getTasksToRemove()} and {@link TaskExpander#getTasksToAdd()}
     *
     * @throws DataException if there's a problem with the data specification
     */
    public void expand() throws TaskException {

        this.findPatterns();

        // there are no patterns or just "**"s
        if (this.inputTrans == null && this.inputCarth == null){

            // can't have "*"-patterns in outputs since there are none in inputs
            if (this.outputTrans != null){
               throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId());
            }
            // expand "**" here if needed
            if (this.inputHere != null){
                this.expandHere();
            }
            return;
        }
        // "**" are not compatible with other types
        else if (this.inputHere != null){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, Process.getInstance().getInputFile());
        }
        // if there are "*"s, expand them all at a time
        else if (this.inputTrans != null){

            Vector<String> taskInput = this.task.getInput();
            Vector<String> matches = this.expandPattern(taskInput.get(this.inputTrans.firstElement()), true);
            Vector<TaskDescription> expTasks = new Vector<TaskDescription> (matches.size());

            for (String match : matches){
                expTasks.add(this.task.expand(match));
            }
            this.expansions.put(this.task, expTasks);
        }

        // expand "***"s -- one by one
        if (this.inputCarth != null){
            this.expandCarthesian();
        }

        // check if all the outputs have "*"s (otherwise, there's no point in using "*" or "***" for inputs)
        if (this.outputTrans == null || this.outputTrans.size() != task.getOutput().size()){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId());
        }

        // expand outputs and dependent tasks using the expanded task name
        this.expandOutputsAndDeps();

        // remove dependencies of all original unexpanded tasks (which are selected for removal)
        for (TaskDescription t : this.expansions.keySet()){
            t.looseAllDeps();
        }

    }


    /**
     * Returns the result of the expansion - a list of new expanded tasks that should be added to
     * the plan (in the topological order of the original tasks from which they originate).
     *
     * @return the new expanded tasks to be added to the plan
     */
    public Collection<TaskDescription> getTasksToAdd(){

        Vector<TaskDescription> all = new Vector<TaskDescription>();
        TaskDescription [] arr = null;

        for (Vector<TaskDescription> expansion : this.expansions.values()){
            all.addAll(expansion);
        }

        arr = all.toArray(new TaskDescription[0]);
        Arrays.sort(arr);

        return Arrays.asList(arr);
    }

    /**
     * Returns the result of the expansion - a list of tasks that should be removed from the
     * plan completely (as their expansions are added to the plan).
     *
     * @return the old tasks to be removed from the plan
     */
    public Collection<TaskDescription> getTasksToRemove(){

        return this.expansions.keySet();
    }


    /**
     * Expands all the "**"s in the task inputs specification.
     *
     * TODO it would be better to create a completely new task object, so that it doesn't get confusing
     */
    private void expandHere() {
        
        Vector<String> taskInput = this.task.getInput();
        
        for (int i = this.inputHere.size() - 1; i <= 0; ++i) {

            int pos = this.inputHere.get(i);
            Vector<String> files = this.expandPattern(taskInput.get(pos), false);

            task.replaceInput(pos, files);
        }
    }

    /**
     * Expands all the "***"s in the task inputs specification.
     */
    private void expandCarthesian() {

        Vector<String> taskInput = this.task.getInput();

        // iterate over all possible "***" patterns and expand them one by one
        for (Integer pos : this.inputCarth) {

            Vector<String> matches = this.expandPattern(taskInput.get(pos), true);
            Vector<TaskDescription> nextExp = new Vector<TaskDescription>();

            for (String match : matches) {

                if (this.expansions.get(this.task) != null){ // some "*"'s and/or later iterations

                    for (TaskDescription t : this.expansions.get(this.task)) {
                        nextExp.add(t.expand(match, pos));
                    }
                }
                else { // pure "***" and first iteration
                    nextExp.add(this.task.expand(match, pos));
                }
            }
            this.expansions.put(this.task, nextExp);
        }
    }


    /**
     * Tries to find patterns in input and output specifications of the task.
     */
    private void findPatterns() {

        this.inputTrans = this.task.getInputPatternPos("*");
        this.inputHere = this.task.getInputPatternPos("**");
        this.inputCarth = this.task.getInputPatternPos("***");
        this.outputTrans = this.task.getOutputPatternPos("*");
    }

    /**
     * Expands a pattern for corresponding file names. Returns just the expansions or file names
     * as a whole.
     * @param pattern the pattern to be expanded
     * @param justExpansions should it return just the expansions ?
     * @return expansions or file names corresponding to the pattern
     */
    private Vector<String> expandPattern(String pattern, boolean justExpansions) {

        String dirName = null, filePattern = null;
        Vector<String> ret = new Vector<String>();
        String [] files;

        // split directory and file name pattern, since findPattern recognizes only such
        // patterns that have just one or more subsequent stars in the file name, the dirName
        // must be always a valid path name, not a pattern, and the filePattern must not be empty
        if (pattern.indexOf(File.separator) != -1){
            dirName = pattern.substring(0, pattern.lastIndexOf(File.separator));
            filePattern = pattern.substring(pattern.lastIndexOf(File.separator) + 1);
        }
        else {
            dirName = ".";
            filePattern = pattern;
        }

        filePattern = filePattern.replaceFirst("\\*+", "*"); // ensure we have just one star in the pattern
        files = new File(dirName).list();

        // no files in the directory, just return empty list
        if (files == null){
            return ret;
        }
        // find all matching files and push the expansions or whole file names to the results list
        for (String file : files){

            String expansion;

            if ((expansion = this.matches(filePattern, file)) != null && new File(dirName + File.separator + file).isFile()){
                if (justExpansions){
                    ret.add(expansion);
                }
                else {
                    ret.add(dirName + File.separator + file);
                }
            }
        }
        // return the result
        return ret;
    }


    /**
     * Replaces all patterns in output file names according to the expansion value of input
     * patterns, replaces patterns in dependent tasks accordingly.
     *
     */
    private void expandOutputsAndDeps() throws TaskException {

        Vector<TaskDescription> deps = this.task.getDependent();

        // expand outputs for all tasks to which the original first task expanded
        for (TaskDescription t : this.expansions.get(this.task)){
            // find out to what was the input pattern expanded
            String expPat = t.getPatternReplacement();
            Vector<String> outputs = t.getOutput();

            // copy the pattern expansion to the input
            for (int i = 0; i < outputs.size(); ++i){
                outputs.set(i, outputs.get(i).replace("*", expPat));
            }
        }

        // expand dependent tasks
        if (deps != null){
            for (TaskDescription dep : deps){
                this.expandDependent(this.task, dep);
            }
        }
    }


    /**
     * Expands all dependent tasks, given an expanded task and an unexpanded one, which depends on it.
     * @param anc the already expanded task
     * @param task its dependent, not yet expanded task
     * @throws DataException if there are some problems with the tasks specifications
     */
    private void expandDependent(TaskDescription anc, TaskDescription task) 
            throws TaskException {

        Vector<TaskDescription> exps = null;

        // this means there are no more dependent expansions and we need only to put all outputs
        // from expanded anc as inputs to this task
        if (!task.hasOutputPattern("*")){

            Vector<String> replacements = new Vector<String>();
            Vector<String> taskInput = task.getInput();
            Vector<Integer> patterns = task.getInputPatternPos("*");

            for (TaskDescription ancExp : this.expansions.get(anc)){ // find pattern replacements
                replacements.add(ancExp.getPatternReplacement());
            }
            for (int i = patterns.size() - 1; i >= 0; --i){ // apply them in inputs

                Vector<String> files = new Vector<String>(replacements.size());
                String pattern = taskInput.get(patterns.get(i));

                for (String rep : replacements){ // all replacements for each "*"-input
                    files.add(pattern.replace("*", rep));
                }

                task.replaceInput(patterns.get(i), files);
            }
            return;
        }

        // now we know the expansion line continues, we need to expand this task
        
        // prepare data structures
        exps = new Vector<TaskDescription>(this.expansions.get(anc).size());
        this.expansions.put(task, exps);

        // expand the "task", according to the expansions of anc
        for (TaskDescription ancExp : this.expansions.get(anc)){
            
            TaskDescription expanded = task.expand(ancExp.getPatternReplacement()); // this expands "*"s

            exps.add(expanded);
            expanded.looseDeps(anc.getId());
            expanded.setDependency(ancExp);
        }

        // if there are "**" or "***" left to be expanded, we can't expand outputs and go deeper, yet
        if (task.hasInputPattern("**") || task.hasInputPattern("***")){
            return;
        }

        // now continue to outputs and dependent tasks expansion

        Vector<String> outputs = task.getOutput();

        // if there are some pattern and some non-pattern outputs, something is wrong
        if (task.getOutputPatternPos("*").size() != outputs.size()){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, task.getId());
        }

        for (TaskDescription exp : exps){

            for (int i = 0; i < outputs.size(); ++i){
                exp.replaceOutput(i, outputs.get(i).replace("*", exp.getPatternReplacement()));
            }
        }

        // go deeper only if there are "*"s ("**" and pure-"***" cannot be expanded yet)
        Vector<TaskDescription> deps = task.getDependent();

        for (TaskDescription dep : deps){

            if (dep.hasInputPattern("*")){
                this.expandDependent(task, dep);
            }
        }

    }

    /**
     * Matches a file name pattern against a real file name. Only patterns
     * with just one single "*" are supported. Returns null or the expansion of the pattern.
     *
     * @param pattern the pattern (see detailed method description for restrictions)
     * @param fileName the file name to match against the pattern
     * @return the expansion of the pattern if succesful, null otherwise
     */
    private String matches(String pattern, String fileName) {

        String beg = pattern.substring(0, pattern.indexOf("*"));
        String end = pattern.endsWith("*") ? "" : pattern.substring(pattern.indexOf("*") + 1);

        if (fileName.startsWith(beg) && fileName.endsWith(end)
                && fileName.length() >= beg.length() + end.length()){
            return fileName.substring(beg.length(), fileName.length() - end.length());
        }
        return null;
    }

}
