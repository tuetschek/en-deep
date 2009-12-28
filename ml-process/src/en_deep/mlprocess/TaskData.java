/*
 *  Copyright (c) 2009 Ondrej Dusek
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

import en_deep.mlprocess.DataSourceDescription.DataSourceType;
import en_deep.mlprocess.Task.TaskType;
import en_deep.mlprocess.exception.DataException;
import java.util.Vector;

/**
 *
 * @author Ondrej Dusek
 */
class TaskData {

    /* CONSTANTS */

    /** Possible types of a data sources sections within the task specification */
    enum DataSourcesSection {
        NONE, TRAIN, DEVEL, EVAL, INPUT, OUTPUT, DATA
    }

    /** Possible usage of data sources in terms of {@link Task} dependencies (see {@link Plan.Occurrences}) */
    enum DataSourcePurpose {
        INPUT, OUTPUT
    }

    /** Possible types of algorithms (for different task types) */
    enum AlgorithmType {
        ALGORITHM, FILTER, METRIC
    }

    /** Prefix for id's of tasks that are system-generated (in parallelization) */
    private static final String GENERATED_TASK_PREFIX = "##GENERATED##";

    /* DATA */

    /** The type of the {@link Task} - one of the possible {@link TaskType} values */
    private TaskType type;
    /** The global id of the {@link Task} from the Scenario file */
    private String id;
    /** The description of the algorithm used in the processing */
    private AlgorithmDescription algorithm;

    /** Data sets (only for {@link Evaluation} tasks) */
    private Vector<DataSetDescription> dataSets;
    /** Training data sets (only for {@link Computation} tasks) */
    private Vector<DataSourceDescription> trainSets;
    /** Development data sets (only for {@link Computation} tasks) */
    private Vector<DataSetDescription> develSets;
    /** Evaluation data sets (only for {@link Computation} tasks) */
    private Vector<DataSourceDescription> evalSets;
    /** The input of this task (may contain files, data sets or features) */
    private Vector<DataSourceDescription> input;
    /** The output of this task (may contain files, data sets or features) */
    private Vector<DataSourceDescription> output;

    /** The data sources section that is currently open */
    private DataSourcesSection open;

    /** All the Tasks that this Task depends on */
    private Vector<TaskData> iDependOn;
    /** All the Task that are depending on this one */
    private Vector<TaskData> dependOnMe;

    /**
     * Ratio by which the parallelization applied to this {@link Task} should be reduced,
     * because there are that many other tasks that will be performed in parallel.
     */
    private int parallelizationRatio;

    /** This serves for generating tasks id in parallelization */
    static private int lastId = 0;

    /* METHODS */

    /**
     * Constructor - creates an empty task with given type and id. This is used
     * when creating tasks from the scenario XML file in {@link ScenarioParser}.
     *
     * @param taskType the desired {@link TaskType}
     * @param id the global id of the {@link Task}
     * @throws DataException for null or empty {@link id} parameter
     */
    TaskData(TaskType taskType, String id) throws DataException {

        if (id == null || id.equals("")){
            throw new DataException(DataException.ERR_INVALID_ID);
        }

        this.type = taskType;
        this.id = id;
        this.parallelizationRatio = 1;
    }

    /**
     * Returns the global id of the task in the process.
     * @return the global id of the task
     */
    String getId() {
        return this.id;
    }

    /**
     * Returns the type of the task.
     * @return the task's type
     */
    TaskType getType() {
        return this.type;
    }

    /**
     * The description of the algorithm used for the {@link Task}.
     * @return the algorithm class, parameters and parallelizability
     */
    AlgorithmDescription getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Sets the algorithm specification - the class name, parameters, parallelizability.
     * 
     * @param className the algorithm class name
     * @param parameters the algorithm parameters
     * @param parallelizable true if the algorithm is parallelizable
     * @throws DataException if the algorithm is already set or if parallelizability is specified for a {@link Manipulation} task.
     */
    void setAlgorithm(AlgorithmType type, String className, String parameters, boolean parallelizable) throws DataException {

        if (this.algorithm != null){
            throw new DataException(DataException.ERR_ALGORITHM_ALREADY_SET);
        }
        if ((this.type == TaskType.COMPUTATION && type != AlgorithmType.ALGORITHM)
                || (this.type == TaskType.EVALUATION && type != AlgorithmType.FILTER)
                || (this.type == TaskType.MANIPULATION && type != AlgorithmType.METRIC)){
            throw new DataException(DataException.ERR_INVALID_ALGORITHM_TYPE);
        }
        if (this.type == TaskType.MANIPULATION && parallelizable){
            throw new DataException(DataException.ERR_CANNOT_PARALELIZE_MANIPULATION);
        }
        this.algorithm = new AlgorithmDescription(className, parameters, parallelizable);
    }

    /**
     * Returns all the output files/data sets/features of this {@link Task}.
     * @return the set-up input
     */
    Vector<DataSourceDescription> getInputDataSources() {

        Vector<DataSourceDescription> ids = new Vector<DataSourceDescription>();

        if (this.type == TaskType.COMPUTATION){

            // add input features for all input data sets
            for (DataSourceDescription dsdFeat : this.input){

                FeatureDescription feat = (FeatureDescription) dsdFeat;
                Vector<DataSourceDescription> allSets = new Vector<DataSourceDescription>();

                allSets.addAll(this.trainSets);
                allSets.addAll(this.develSets);
                allSets.addAll(this.evalSets);

                for (DataSourceDescription dsdDS : allSets){
                    if (dsdDS.type != DataSourceType.DATA_SET){
                        continue;
                    }
                    DataSetDescription ds = (DataSetDescription) dsdDS;
                    ids.add(new FeatureDescription(ds.id + "::" + feat.id));
                }
            }

            // add output features in input data sets as input features
            for (DataSourceDescription dsdFeat : this.output){

                FeatureDescription feat = (FeatureDescription) dsdFeat;
                Vector<DataSourceDescription> inputSets = new Vector<DataSourceDescription>();

                inputSets.addAll(this.trainSets);
                inputSets.addAll(this.develSets);

                for (DataSourceDescription dsdDS : inputSets){
                    if (dsdDS.type != DataSourceType.DATA_SET){
                        continue;
                    }
                    DataSetDescription ds = (DataSetDescription) dsdDS;
                    ids.add(new FeatureDescription(ds.id + "::" + feat.id));
                }
            }

            // add the data sets themselves
            ids.addAll(this.trainSets);
            ids.addAll(this.develSets);
            ids.addAll(this.evalSets);
        }
        else if (this.type == TaskType.MANIPULATION){

            // just get the input data sets
            ids.addAll(this.input);
        }
        else { // TaskType.EVALUATION

            // add all the features for the input data set
            for (DataSourceDescription dsdFeat : this.input){

                FeatureDescription feat = (FeatureDescription) dsdFeat;

                for (DataSourceDescription dsdDS : this.dataSets){
                    DataSetDescription ds = (DataSetDescription) dsdDS;
                    ids.add(new FeatureDescription(ds.id + "::" + feat.id));
                }
            }
        }

        return ids;
    }

    /**
     * Returns all the output files/data sets/features of this {@link Task}.
     * @return the set-up output
     */
    Vector<DataSourceDescription> getOutputDataSources() {

        Vector<DataSourceDescription> ods = new Vector<DataSourceDescription>();

        if (this.type == TaskType.COMPUTATION){

            // add all output features in output data sets
            for (DataSourceDescription dsdFeat : this.output){

                FeatureDescription feat = (FeatureDescription) dsdFeat;

                for (DataSourceDescription dsdDS : this.evalSets){
                    if (dsdDS.type != DataSourceType.DATA_SET){
                        continue;
                    }
                    DataSetDescription ds = (DataSetDescription) dsdDS;
                    ods.add(new FeatureDescription(ds.id + "::" + feat.id));
                }
            }
        }
        else { // MANIPULATION or EVALUATION - add output data sets or files

            ods.addAll(this.output);
        }

        return ods;
    }

    /**
     * This makes a copy of the given TaskData, without all the data sources ({@link input},
     * {@link output} etc. The id of the new taskData is generated.
     *
     * @return a copy of this {@link TaskData} with no data sources set-up.
     */
    private TaskData cloneWithoutDataSources() {

        TaskData copy = null;
        
        try {
            copy = new TaskData(this.type, TaskData.generateId());
        }
        catch(DataException e){ // this should never happen, as we never pass an empty id
            return null;
        }

        copy.algorithm = this.algorithm;
        copy.open = this.open;
        copy.parallelizationRatio = this.parallelizationRatio;

        return copy;
    }

    /**
     * Checks all the data sets-related constraints, throws a {@link DataException} if the object doesn't
     * conform to them. Checks for an open data sources section as well.
     *
     * @throws DataException if the object doesn't fulfill all the data sets-related constraints
     */
    void checkDataSets() throws DataException{

        if (this.input == null || this.output == null){
            throw new DataException(DataException.ERR_NO_IN_OR_OUT);
        }

        if (this.type == TaskType.EVALUATION && this.dataSets == null){
            throw new DataException(DataException.ERR_NO_DATA_SET);
        }
        if (this.type == TaskType.COMPUTATION){
            if (this.trainSets == null){
                throw new DataException(DataException.ERR_NO_TRAIN_SET);
            }
            if (this.evalSets == null){
                throw new DataException(DataException.ERR_NO_EVAL_SET);
            }
            if ((this.evalSets != null && this.evalSets.size() != this.trainSets.size())
                    || (this.develSets != null && this.develSets.size() != this.trainSets.size())){
                throw new DataException(DataException.ERR_NO_MATCHING_DATA_NUMBERS);
            }
        }
        if (this.open != DataSourcesSection.NONE){
            throw new DataException(DataException.ERR_OPEN_DATA_SECTION);
        }
    }

    /**
     * Opens a new data sources section, checking all section constraints.
     *
     * @param section the type of the new data sources section
     * @throws DataException if there's a section open or a duplicate section is encountered, or the section \
     *      doesn't fit into the task type
     */
    void openDataSection(DataSourcesSection section) throws DataException {

        if (this.open != DataSourcesSection.NONE){
            throw new DataException(DataException.ERR_NESTED_DATA_SECTIONS);
        }

        this.open = section;
        switch(this.open){
            case DATA: // data sections only allowed for evaluation
                if (this.dataSets != null || this.type != TaskType.EVALUATION){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.dataSets = new Vector<DataSetDescription>();
                break;
            case DEVEL: // development sections only allowed for computation
                if (this.develSets != null || this.type != TaskType.COMPUTATION){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.develSets = new Vector<DataSetDescription>();
                break;
            case EVAL: // eval sections only allowed for computation
                if (this.evalSets != null || this.type != TaskType.COMPUTATION){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.evalSets = new Vector<DataSourceDescription>();
                break;
            case INPUT:
                if (this.input != null){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.input = new Vector<DataSourceDescription>();
                break;
            case NONE: // a "none" section doesn't make any sense
                throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
            case OUTPUT:
                if (this.output != null){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.input = new Vector<DataSourceDescription>();
                break;
            case TRAIN: // training sections only allowed for computation
                if (this.trainSets != null || this.type != TaskType.COMPUTATION){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.trainSets = new Vector<DataSourceDescription>();
                break;
        }
    }


    /**
     * Tries to close a data sources section. Throws an exception if the section type
     * doesn't fit the open section or if there's no section open.
     *
     * @param type the type of the section to be closed
     * @throws DataException if the type is invalid
     */
    void closeDataSection(DataSourcesSection type) throws DataException {

        if (type == DataSourcesSection.NONE || this.open != type){
             throw new DataException(DataException.ERR_INVALID_DATA_SECTION_CLOSE);
        }
        this.open = DataSourcesSection.NONE;
    }

    /**
     * Adds a data source to the current section, checks if the data source type is compatible with the
     * section type.
     * @param desc the data source to be added
     * @throws DataException if the data source doesn't fit into the currently open data sources section
     */
    void addDataSource(DataSourceDescription desc) throws DataException {

        switch (this.open){
            case DATA:
                if (desc.type != DataSourceType.DATA_SET){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.dataSets.add((DataSetDescription) desc);
                break;

            case DEVEL:
                if (desc.type != DataSourceType.DATA_SET){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.dataSets.add((DataSetDescription) desc);
                break;

            case EVAL:
                if ((desc.type != DataSourceType.DATA_SET && desc.type != DataSourceType.FILE)
                        || (this.evalSets.size() > 0 && this.evalSets.elementAt(0).type != desc.type)){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.evalSets.add(desc);
                break;

            case INPUT:
                if ((this.type == TaskType.COMPUTATION && desc.type != DataSourceType.FEATURE)
                        || (this.type == TaskType.MANIPULATION && (desc.type != DataSourceType.DATA_SET || desc.type != DataSourceType.FILE))
                        || (this.type == TaskType.EVALUATION && desc.type != DataSourceType.FEATURE)){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.input.add(desc);
                break;

            case OUTPUT:
                if ((this.type == TaskType.COMPUTATION && desc.type != DataSourceType.FEATURE)
                        || (this.type == TaskType.MANIPULATION && (desc.type != DataSourceType.DATA_SET || desc.type != DataSourceType.FILE))
                        || (this.type == TaskType.EVALUATION && (desc.type != DataSourceType.FILE || this.output.size() > 0))){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.output.add(desc);
                break;

            case TRAIN:
                if ((desc.type != DataSourceType.DATA_SET && desc.type != DataSourceType.FILE)
                        || (this.trainSets.size() > 0 && this.trainSets.elementAt(0).type != desc.type)){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.trainSets.add(desc);
                break;

            default:
                throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
        }
    }

    /**
     * Sets a dependency for this task (i.e\. marks this {@link TaskData} as depending
     * on the parameter).
     *
     * @param source the governing {@link TaskData} that must be processed before this one.
     */
    void setDependency(TaskData source) {

        if (this.iDependOn == null){
            this.iDependOn = new Vector<TaskData>();
        }
        this.iDependOn.add(source);

        if (source.dependOnMe == null){
            source.dependOnMe = new Vector<TaskData>();
        }
        source.dependOnMe.add(this);
    }


    /**
     * Splits a task by data sources, if it's to be performed on more of them. This serves
     * for better parallelization of the whole process.
     *
     * @return An array of split tasks, which are to be performed on one data source only.
     */
    TaskData [] split (){

        TaskData [] splitTasks = null;

        if (this.type == TaskType.COMPUTATION){

            splitTasks = new TaskData[this.trainSets.size()];

            for (int i = 0; i < splitTasks.length; ++i){
                splitTasks[i] = this.cloneWithoutDataSources();
                splitTasks[i].input = this.input;
                splitTasks[i].output = this.output;

                splitTasks[i].trainSets = new Vector<DataSourceDescription>(1);
                splitTasks[i].trainSets.add(this.trainSets.get(i));
                if (this.develSets.size() > i){
                    splitTasks[i].develSets = new Vector<DataSetDescription>(1);
                    splitTasks[i].develSets.add(this.develSets.get(i));
                }
                if (this.evalSets.size() > i){
                    splitTasks[i].evalSets = new Vector<DataSourceDescription>(1);
                    splitTasks[i].evalSets.add(this.evalSets.get(i));
                }
                splitTasks[i].parallelizationRatio *= splitTasks.length;
            }
        }
        else if (this.type == TaskType.EVALUATION){

            splitTasks = new TaskData[this.dataSets.size()];

            for (int i = 0; i < splitTasks.length; ++i){

                // TODO vyřešit problém s počtem souborů - mělo by jich být stejně jako datasetů
            }
        }

        return splitTasks;
    }

    /**
     * Returns an ID for a generated task (in parallelization).
     *
     * @return a generated ID string
     */
    private static synchronized String generateId(){

        String taskId;

        lastId++;
        taskId = GENERATED_TASK_PREFIX + lastId;

        return taskId;
    }

    
    /* INNER CLASSES */


    /**
     * This is used to store algoritm description of tasks.
     * There are just members, all are public.
     */
    class AlgorithmDescription {

        /* DATA */

        /** The name of the Task class that should process the {@link Task} */
        String className;
        /** The parameters that should be used in the computation, the class parses them by itself */
        String parameters;
        /**
         * This denotes if the algorithm may be parallelized - not applicable for {@link Maninpulation}
         * type tasks.
         */
        boolean parallelizable;

        /* METHODS */
        
        /**
         * A constructor that just sets the values of all members
         * @param className The name of the class that should process the @{link Task}
         * @param parameters The processing parameters
         * @param parallelizable The parallelizability setting
         */
        private AlgorithmDescription(String className, String parameters, boolean parallelizable) {
            this.className = className;
            this.parameters = parameters;
            this.parallelizable = parallelizable;
        }
    }
}
