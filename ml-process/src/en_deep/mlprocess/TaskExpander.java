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

import com.google.common.collect.HashMultimap;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.StringUtils;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;


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

    /* CONSTANTS */

    /* DATA */

    /** The task to be processed */
    private TaskDescription task;

    /** Expansion pattern matches for all affected tasks */
    private HashMultimap<TaskDescription, TaskDescription> expansions;


    /* METHODS */

    /**
     * This creates a new {@link TaskExpander} object, just setting the {@link TaskDescription} to be
     * processed. The expansion process is triggered by {@link TaskExpander#expand()}
     *
     * @param task the task to be expanded
     */
    public TaskExpander(TaskDescription task){

        this.task = task;
        this.expansions = HashMultimap.create();
    }


    /**
     * The actual expansion process. When it's finished, the new tasks and tasks to be deleted may be
     * retrieved using {@link TaskExpander#getTasksToRemove()} and {@link TaskExpander#getTasksToAdd()}
     *
     * @throws TaskException if there's a problem with the data specification
     */
    public void expand() throws TaskException {

        if (!this.task.hasInputPatterns()){
            if (this.task.hasOutputPatterns(false)){
               throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId(),
                       "No '*' patterns in inputs but some in outputs.");
            }
            return;
        }

        // check if all the outputs have "*" or "**"'s (otherwise, there's no point in using "*" for inputs)
        if (this.task.hasInputPatterns(false) && !this.task.allOutputPatterns()){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId(),
                    "All outputs must have '*' or '**' patterns if inputs have '*' patterns.");
        }

        // expand the inputs
        this.expandInputs();

        // expand outputs and dependent tasks using the expanded task name (if we can--"**"'s are never expanded)
        if (!this.task.hasOutputPatterns(true)){
            this.expandOutputsAndDeps(this.task);
        }

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

        all.addAll(this.expansions.values());

        arr = all.toArray(new TaskDescription[0]);
        Arrays.sort(arr, new TaskDescription.TopologicalComparator());

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
     * This expands outputs for all tasks to which the original task expanded, according to their
     * pattern replacements. It assumes the expansions of the task are already located in {@link #expansions}.
     * @param original the original, unexpanded task
     */
    private void expandOutputs(TaskDescription original) throws TaskException {

        // if there are some pattern and some non-pattern outputs, something is wrong
        if (task.getOutputPatternPos(false).size() != task.getOutput().size()){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, task.getId(),
                    "Some outputs have '*' patterns and some don't.");
        }
    
        for (TaskDescription t : this.expansions.get(original)) {
            // find out to what was the input pattern expanded
            String expPat = t.getPatternReplacement();
            Vector<String> outputs = t.getOutput();

            // copy the pattern expansion to the input
            for (int i = 0; i < outputs.size(); ++i) {
                if (outputs.get(i).matches("\\*\\|")){
                    throw new TaskException(TaskException.ERR_PATTERN_SPECS, t.getId(), "Extended patterns not allowed"
                            + " on the output.");
                }
                outputs.set(i, outputs.get(i).replace("*", expPat));
            }
        }
    }

    /**
     * Returns a list of all files in the given directory. Throws an exception if there are none.
     * @param path the desired directory
     * @return a list of all files in the given directory
     * @throws TaskException if there are no files in the directory
     */
    private String[] getFilesInDir(String dir) throws TaskException {

        String [] files = new File(dir).list();
        // no files in the directory
        if (files == null) {
            throw new TaskException(TaskException.ERR_NO_FILES, this.task.getId(), "(" + dir + ")");
        }
        return files;
    }


    /**
     * Replaces all patterns in output file names according to the expansion value of input
     * patterns, replaces patterns in dependent tasks accordingly. 
     * Calls {@link #expandDependent(TaskDescription, TaskDescription)},
     * which calls this method recursively.
     *
     * @param expTask the task whose outputs and dependencies should be expanded
     */
    private void expandOutputsAndDeps(TaskDescription expTask) throws TaskException {

        this.expandOutputs(expTask);

        Set<TaskDescription> deps = expTask.getDependent();

        // expand dependent tasks, only if they have '*'-patterns (cannot expand for '**', yet)
        if (deps != null){
            for (TaskDescription dep : deps){

                if (!this.expansions.containsKey(dep) && dep.hasInputPatterns(false) && !dep.hasInputPatterns(true)){
                    this.expandDependent(expTask, dep);
                }
                else if (this.expansions.containsKey(dep)){
                    for (TaskDescription depExp : this.expansions.get(dep)){
                        this.cleanPrerequisites(depExp);
                    }
                }               
            }
        }

        Set<TaskDescription> pres = expTask.getPrerequisites();

        if (pres != null){
            for (TaskDescription pre: pres){
                if (!this.expansions.containsKey(pre) && pre.hasOutputPatterns(false) && !pre.hasOutputPatterns(true)){
                    this.expandPrerequisite(pre, expTask);
                }
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

        // this means there are no more dependent expansions and we need only to put all outputs
        // from expanded anc as inputs to this task
        if (!task.hasOutputPatterns()){

            Vector<String> replacements = new Vector<String>();
            Vector<String> taskInput = task.getInput();
            Vector<Integer> patterns = task.getInputPatternPos(false);

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

        // expand the "task", according to the expansions of anc
        task.removeDependencies(this.expansions.get(anc));
        for (TaskDescription ancExp : this.expansions.get(anc)){
            
            TaskDescription expanded = task.expand(ancExp.getPatternReplacements()); // this expands "*"s

            expanded.setDependency(ancExp);
            this.cleanPrerequisites(expanded);
            this.expansions.put(task, expanded);
        }

        // if there are "**" or "***" left to be expanded, we can't expand outputs and go deeper, yet
        if (task.hasOutputPatterns(true) || task.hasInputPatterns(true)){
            return;
        }

        // now continue to outputs and dependent tasks expansion
        this.expandOutputsAndDeps(task);
    }

    /**
     * Given a task which is already expanded, this expands its prerequisite. It is possible that
     * the prerequisite will be expanded only partially (if it contains both "**" and "*" inputs),
     * but all "*"-variables must be expanded, or the patterns haven't been set up correctly.
     * @param pre the prerequisite task
     * @param task the task depending on pre, which is already expanded
     * @throws TaskException if the expansion is not possible
     */
    private void expandPrerequisite(TaskDescription pre, TaskDescription task) throws TaskException {

        int [] commonVars = this.findCommonVariables(pre, task);

        for (TaskDescription taskExp : this.expansions.get(task)){

            TaskDescription expanded = pre.expand(taskExp.getPatternReplacements(commonVars));

            if (expanded.hasInputPatterns(false)){
                throw new TaskException(TaskException.ERR_PATTERN_SPECS, pre.getId(), "Could not expand"
                        + " a prerequisite task completely.");
            }
            this.cleanDependent(expanded);
            this.expansions.put(pre, expanded);
        }

        this.expandOutputsAndDeps(pre);
    }

    /**
     * This finds the variables in the inputs of the depending task which should originate in the outputs
     * of a prerequisite task.
     * @param pre the prerequisite task
     * @param dep the dependent task
     * @return a list of shared variables
     * @throws TaskException if the tasks don't depend on each other
     */
    private int [] findCommonVariables(TaskDescription pre, TaskDescription dep) throws TaskException {

        Vector<String> preOut = new Vector<String>(pre.getOutput().size());
        Vector<String> depIn = dep.getInput();
        String common = null;

        for (String out : pre.getOutput()){
            preOut.add(StringUtils.getOccurencePattern(out));
        }
        for (String in : depIn){
            if (preOut.contains(StringUtils.getOccurencePattern(in))){
                common = in;
            }
        }
        if (common == null){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, pre.getId(), "Non-existent dependency"
                    + " to " + dep.getId());
        }
        return StringUtils.findPatternVariables(common);
    }

    /**
     * This removes all the unnecessary expansions of prerequisites of the given task. E.g. if
     * task2#a is an expanded version of task2 which depended on task1, and there exist expansions
     * task1#a and task1#b, by default task2#a depends on both of them. This removes task1#b from
     * the dependency list.
     *
     * @todo optimize -- this is really expensive (creates a hash-set every time)
     * @param expTask the task to be processed
     */
    private void cleanPrerequisites(TaskDescription expTask) {

        Collection<TaskDescription> values = this.expansions.values();
        Set<TaskDescription> prerequisites = expTask.getPrerequisites();

        if (prerequisites == null){
            return;
        }
        for (TaskDescription pre : prerequisites){
            if (values.contains(pre)
                    && !expTask.getPatternReplacement().equals(pre.getPatternReplacement())){
                pre.removeDependency(expTask);
            }
        }
    }


    /**
     * This is symmetric to {@link #cleanPrerequisites(TaskDescription)}, it clears all the
     * unnecessary dependencies of the task when expanding prerequisites.
     * @param expTask the task to be processed
     */
    private void cleanDependent(TaskDescription expTask){

        Collection<TaskDescription> values = this.expansions.values();
        Set<TaskDescription> dependent = expTask.getDependent();

        if (dependent == null){
            return;
        }
        for (TaskDescription pre : dependent){
            if (values.contains(pre)
                    && !expTask.getPatternReplacement().equals(pre.getPatternReplacement())){
                pre.removeDependency(expTask);
            }
        }
    }

    
    /**
     * This expands all the patterns in the input specifications (with all variables), including the listing mode.
     * @see #findMatches(String)
     * @see #replaceListing(int, HashSet)
     * @see #replaceExpanding(HashSet[]) 
     */
    private void expandInputs() throws TaskException {

        Vector<String> taskIn = this.task.getInput();
        HashSet<String> [] expMode = new HashSet[10]; // expanding mode matches
        HashSet<String> [] listMode = new HashSet[taskIn.size()]; // listing mode matches

         // find variables and matches
        for (int inputNo = 0; inputNo < taskIn.size(); ++inputNo){ 

            HashSet<String> [] matches = this.findMatches(taskIn.get(inputNo));
            
            if (matches[0] != null){ // store listing mode matches, if some
                listMode[inputNo] = matches[0];
            }
            // intersect expanding mode matches for different file patterns and the same variable
            for (int varNo = 1; varNo < 10; ++varNo){ 

                if (matches[varNo] != null){
                    if (expMode[varNo] == null){
                        expMode[varNo] = matches[varNo];
                    }
                    else {
                        expMode[varNo].retainAll(matches[varNo]);
                    }
                }
            }
        }

        // replace listing mode
        for (int inputNo = taskIn.size()-1; inputNo >= 0; --inputNo){

            if (listMode[inputNo] != null){
                this.replaceListing(inputNo, listMode[inputNo]);
            }
        }

        // replace expanding mode
        this.replaceExpanding(expMode);
    }


    /**
     * This splits the directory name and filename and normalizes the pattern. Since findPattern recognizes
     * only such patterns that have just one or more subsequent stars in the file name, the returned directory
     * name must be always a valid path name, not a pattern, and the filePattern must not be empty.
     *
     * @param wholePattern the original, not-normalized file pattern
     * @return the directory name and the normalized file pattern, respectively
     */
    private Pair<String, String> getDirAndFilePattern(String wholePattern){

        String dirName, filePattern;

        if (wholePattern.indexOf(File.separator) != -1){
            dirName = wholePattern.substring(0, wholePattern.lastIndexOf(File.separator));
            filePattern = StringUtils.normalizeFilePattern(StringUtils.truncateFileName(wholePattern));
        }
        else {
            dirName = ".";
            filePattern = StringUtils.normalizeFilePattern(wholePattern);
        }
        return new Pair<String, String>(dirName, filePattern);
    }

    /**
     * This finds the matches for all the variables in the given pattern, including the listing mode variables
     * (which are returned in the zero-index of the filed).
     * @param pattern the pattern for whose variables we should search
     * @return the matches for all possible variables (at non-null indexes in the array)
     */
    private HashSet<String>[] findMatches(String pattern) throws TaskException {

        Pair<String, String> dirFile = this.getDirAndFilePattern(pattern);
        String [] files = this.getFilesInDir(dirFile.first);
        int [] vars = StringUtils.findPatternVariables(dirFile.second);
        HashSet<String> [] matches = new HashSet[10];

        if (vars == null){
            return matches;
        }
        for (String file : files){

            String [] curMatch = StringUtils.matchesEx(file, dirFile.second);

            if (curMatch != null){
                for (int j = 0; j < vars.length; ++j){

                    // expanding mode variables
                    if (matches[vars[j]] == null){
                        matches[vars[j]] = new HashSet<String>();
                    }
                    matches[vars[j]].add(curMatch[j]);
                }
            }
        }
        for (int j = 0; j < vars.length; ++j){
            if (matches[vars[j]] == null){
                throw new TaskException(TaskException.ERR_NO_FILES, this.task.getId(), pattern);
            }
        }
        return matches;
    }

    /**
     * This performs the listing mode replacements. It replaces the given input of the task with the whole listing of
     * successful matches.
     *
     * @param inputNo the position of the input to be replaced
     * @param matches a list of successful matches
     */
    private void replaceListing(int inputNo, HashSet<String> matches) {

        String [] repls = matches.toArray(new String [matches.size()]);
        Arrays.sort(repls);

        for (int j = 0; j < repls.length; ++j){
            repls[j] = StringUtils.replaceEx(task.getInput(inputNo), repls[j], 0);
        }
        task.replaceInput(inputNo, Arrays.asList(repls));
    }

    /**
     * This performs the replacements in expanding mode. It expands the task to a set of tasks for every
     * successful match of every pattern variable.
     * @param matches the matches for the individual variables (1-9, zero position reserved for listing mode)
     * @throws TaskException
     */
    private void replaceExpanding(HashSet<String>[] matches) throws TaskException {

        for (int varNo = 1; varNo < 10; ++varNo){

            if (matches[varNo] == null){
                continue;
            }
            if (matches[varNo].isEmpty()){
                throw new TaskException(TaskException.ERR_NO_FILES, this.task.getId(), "Intersection of patterns"
                        + " for variable " + varNo);
            }
            Vector<TaskDescription> nextExp = new Vector<TaskDescription>();
            Collection<TaskDescription> curExp;

            if (this.expansions.get(this.task).isEmpty()){ // first iteration
                curExp = new Vector<TaskDescription>(1);
                curExp.add(this.task);
            }
            else { // later iterations
                curExp = this.expansions.removeAll(this.task);
            }

            String [] matchesSort = matches[varNo].toArray(new String [matches[varNo].size()]);
            Arrays.sort(matchesSort);
            for (String match : matchesSort) {

                for (TaskDescription t : curExp) {
                    TaskDescription expanded = t.expand(match, varNo);
                    nextExp.add(expanded);
                }
            }
            for (TaskDescription t : curExp){
                if (t != this.task){
                    t.looseAllDeps();
                }
            }
            this.expansions.putAll(this.task, nextExp);
        }
    }

}
