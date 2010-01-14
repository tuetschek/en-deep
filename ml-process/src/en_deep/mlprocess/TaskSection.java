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
import en_deep.mlprocess.manipulation.DataMerger;
import en_deep.mlprocess.manipulation.DataSplitter;
import en_deep.mlprocess.manipulation.FileMerger;
import java.util.Vector;

/**
 * This class is used by the 
 * @author Ondrej Dusek
 */
class TaskSection {

    /* CONSTANTS */

    /** Possible types of a data sources sections within the task specification */
    enum DataSourcesSection {
        NONE, TRAIN, DEVEL, EVAL, INPUT, OUTPUT, DATA, CREATED, NEEDED
    }

    /** Possible usage of data sources in terms of {@link Task} dependencies (see {@link Plan.Occurrences}) */
    enum DataSourcePurpose {
        INPUT, OUTPUT
    }

    /** Possible types of algorithms (for different task types) */
    enum AlgorithmType {
        ALGORITHM, FILTER, METRIC
    }


    /* DATA */

    /** The type of the {@link Task} - one of the possible {@link TaskType} values */
    private TaskType type;
    /** The global id of the {@link Task} from the Scenario file */
    private String id;
    /** The description of the algorithm used in the processing */
    private AlgorithmDescription algorithm;
    /** Is the {@link Task} parallelizable ? */
    private boolean parallelizable;

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

    /** Needed features vector (only for {@link Manipulation} tasks) */
    private Vector<FeatureDescription> needed;
    /** Created features vector (only for {@link Manipulation} tasks) */
    private Vector<FeatureDescription> created;

    /** The data sources section that is currently open */
    private DataSourcesSection open;


    /* METHODS */

    /**
     * Constructor - creates an empty task with given type and id. This is used
     * when creating tasks from the scenario XML file in {@link ScenarioParser}.
     *
     * @param taskType the desired {@link TaskType}
     * @param id the global id of the {@link Task}
     * @throws DataException for null or empty {@link id} parameter
     */
    TaskSection(TaskType taskType, String id) throws DataException {

        if (id == null || id.equals("")){
            throw new DataException(DataException.ERR_INVALID_ID);
        }

        this.type = taskType;
        this.id = id;
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
        this.algorithm = new AlgorithmDescription(className, parameters);
        this.parallelizable = parallelizable;
    }


    /**
     * Checks all the data sets-related constraints, throws a {@link DataException} if the object doesn't
     * conform to them. Checks for an open data sources section as well.
     *
     * @throws DataException if the object doesn't fulfill all the data sets-related constraints
     */
    void checkDataSets() throws DataException {

        if (this.input == null || this.output == null){
            throw new DataException(DataException.ERR_NO_IN_OR_OUT);
        }
        if (this.open != DataSourcesSection.NONE){
            throw new DataException(DataException.ERR_OPEN_DATA_SECTION);
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
        if (this.type == TaskType.MANIPULATION){
            for (DataSourceDescription dsd: this.output){
                // check overlapping input and output data sets - allowed for data sets only & must have
                // some output feature
                if (this.input.contains(dsd)){

                    String dsId;
                    boolean foundFeat = false;

                    // one file as input and output not allowed
                    if (dsd.type != DataSourceType.DATA_SET){ // TODO possibly allow also features on files ?
                        throw new DataException(DataException.ERR_OVERLAPPING_INPUT_OUTPUT);
                    }
                    dsId = ((DataSetDescription) dsd).id;

                    // check for output features in input & output data sets !
                    for (FeatureDescription fd : this.created){
                        if (dsId.equals(fd.dataSetId)){
                            foundFeat = true;
                            break;
                        }
                    }
                    if (!foundFeat){
                        throw new DataException(DataException.ERR_OVERLAPPING_INPUT_OUTPUT);
                    }
                }
            }
        }
    }

    /**
     * Creates {@link Task} descriptions from all the data in the corresponding file
     * section, applying parallelization. 
     *     
     * Parallelizes all tasks that are set as "parallelizable" (in {@link AlgorithmDescription} by
     * creating mutliple {@link TaskDescription}s. Splits the Task, too, if it's to be performed
     * on multiple data sources. Checks for the presence of all needed data sources.
     */
    Vector<TaskDescription> getDescriptions() throws DataException {

        Vector<TaskDescription> tasks = new Vector<TaskDescription>();
        Vector<FeatureDescription> inputFeats;

        this.checkDataSets();

        switch (this.type){
            case COMPUTATION:

                // input & output is features in Computation tasks
                inputFeats = new Vector<FeatureDescription>(this.input.size());
                Vector<FeatureDescription> outputFeats = new Vector<FeatureDescription>(this.output.size());

                for (DataSourceDescription dsd : this.input){
                    inputFeats.add((FeatureDescription) dsd);
                }
                for (DataSourceDescription dsd : this.output){
                    outputFeats.add((FeatureDescription) dsd);
                }
                // create all needed task descriptions for different working sets
                for (int i = 0; i < this.trainSets.size(); ++i){
                    // parallelization is not enabled
                    if (!this.parallelizable){
                        tasks.add(new ComputationDescription(this.id, this.algorithm,
                                this.trainSets.elementAt(i),
                                this.develSets != null ? this.develSets.elementAt(i): null,
                                this.evalSets.elementAt(i),
                                inputFeats, outputFeats));
                    }
                    // parallelize the task
                    else {                        
                        tasks.addAll(this.getParallelizedComputation(i, inputFeats, outputFeats));
                    }
                }
                break;

            case MANIPULATION: // manipulation tasks are not parallelizable / no multiple data sources may be used
                tasks.add(new ManipulationDescription(this.id, this.algorithm, this.input, this.output,
                        this.needed, this.created));
                break;

            case EVALUATION:
                
                inputFeats = new Vector<FeatureDescription>(this.input.size());

                for (DataSourceDescription dsd : this.input){
                    inputFeats.add((FeatureDescription) dsd);
                }

                for (int i = 0; i < this.dataSets.size(); ++i){

                    if (!this.parallelizable){
                        tasks.add(new EvaluationDescription(this.id, this.algorithm,
                                this.dataSets.elementAt(i), inputFeats, (FileDescription) this.output.elementAt(i)));
                    }
                    // parallelize the task
                    else {
                        tasks.addAll(this.getParallelizedEvaluation(i, inputFeats));
                    }
                }
                break;
        }
        return tasks;

    }


    /**
     * Returns all the {@link TaskDescriptions} for the work needed to process this {@link Computation} 
     * {@link TaskSection} in parallel on the given working data set.
     *
     * <p>
     * This respects the {@link Process.getMaxWorkers()} setting and the number of working data sets
     * for this {@link TaskSection} in total, so that the maximum number of created {@link TaskDescriptions}
     * in total is just the number of working data sets * 3 ({@link en_deep.mlprocess.manipulation.DataSplitter})
     * + {@link Process.getMaxWorkers()} + number of working data sets * 3 again
     * {@link en_deep.mlprocess.manipulation.DataMerger}.
     * </p>
     *
     * @param workingDataNo the number of the data sets to use (in the data sets fields' order)
     * @param inputFeats the input features (this.input converted to Vector<FeatureDescription>)
     * @param outputFeats the output features (this.output converted to Vector<FeatureDescription>), null for {@link Evaluation} tasks
     * @throws DataException if the parallelization is to be processed on files, which is not allowed
     * @return
     */
    Vector<TaskDescription> getParallelizedComputation(int workingDataNo, Vector<FeatureDescription> inputFeats,
            Vector<FeatureDescription> outputFeats) throws DataException {

        assert(this.type == TaskType.COMPUTATION);

        int maxParallel = (int) Math.ceil(Process.getInstance().getMaxWorkers() /
                (double)(this.trainSets.size()));
        Vector<TaskDescription> tasks = new Vector<TaskDescription>(6 + maxParallel);

        if (this.trainSets.elementAt(workingDataNo).type != DataSourceType.DATA_SET
                || this.evalSets.elementAt(workingDataNo).type != DataSourceType.DATA_SET){
            throw new DataException(DataException.ERR_CANNOT_PARALELIZE_ON_FILES);
        }

        // no need to parallelize, there's enough working data sets to keep all Workers occupied
        if (maxParallel == 1){

            tasks.add(new ComputationDescription(this.id, this.algorithm, this.trainSets.elementAt(workingDataNo),
                    this.develSets != null ? this.develSets.elementAt(workingDataNo) : null,
                    this.evalSets.elementAt(workingDataNo),
                    inputFeats, outputFeats));
        }
        // now we have to parallelize
        else {
            Vector<DataSourceDescription> splitTrain = new Vector<DataSourceDescription>();
            Vector<DataSourceDescription> splitDevel = new Vector<DataSourceDescription>();
            Vector<DataSourceDescription> splitEval = new Vector<DataSourceDescription>();

            tasks.add(this.getSplitterTask(maxParallel, (DataSetDescription) this.trainSets.elementAt(workingDataNo), splitTrain));
            if (this.develSets != null){
                tasks.add(this.getSplitterTask(maxParallel, this.develSets.elementAt(workingDataNo), splitDevel));
            }
            tasks.add(this.getSplitterTask(maxParallel, (DataSetDescription) this.evalSets.elementAt(workingDataNo), splitEval));

            for (int i = 0; i < maxParallel; ++i){
                tasks.add(new ComputationDescription(this.id, this.algorithm,
                        splitTrain.elementAt(i),
                        this.develSets != null ? splitDevel.elementAt(i) : null,
                        splitEval.elementAt(i), inputFeats, outputFeats)); // the feats are ok this way, the data set id is added to them later
            }

            tasks.add(this.getMergerTask(splitTrain, this.trainSets.elementAt(workingDataNo)));
            if (this.develSets != null){
                tasks.add(this.getMergerTask(splitDevel, this.develSets.elementAt(workingDataNo)));
            }
            tasks.add(this.getMergerTask(splitEval, this.trainSets.elementAt(workingDataNo)));
        }

        return tasks;
    }

    /**
     * Returns all the {@link TaskDescriptions} for the work needed to process this {@link Evaluation}
     * {@link TaskSection} in parallel on the given working data set.
     *
     * <p>
     * This respects the {@link Process.getMaxWorkers()} setting and the number of working data sets
     * for this {@link TaskSection} in total, so that the maximum number of created {@link TaskDescriptions}
     * in total is just the number of working data sets ({@link en_deep.mlprocess.manipulation.DataSplitter})
     * + {@link Process.getMaxWorkers()} + number of working data sets again
     * {@link en_deep.mlprocess.manipulation.DataMerger}.
     * </p>
     *
     * @param workingDataNo the number of the data sets to use (in the data sets fields' order)
     * @param inputFeats the input features (this.input converted to Vector<FeatureDescription>)
     * @return
     */
    Vector<TaskDescription> getParallelizedEvaluation(int workingDataNo, Vector<FeatureDescription> inputFeats)
        throws DataException {

        assert(this.type == TaskType.EVALUATION);

        int maxParallel = (int) Math.ceil(Process.getInstance().getMaxWorkers() / (double)(this.dataSets.size()));
        Vector<TaskDescription> tasks = new Vector<TaskDescription>(2 + maxParallel);

        // no need to parallelize, there's enough working data sets to keep all Workers occupied
        if (maxParallel == 1){

            tasks.add(new EvaluationDescription(this.id, this.algorithm,
                    this.dataSets.elementAt(workingDataNo),
                    inputFeats, (FileDescription) this.output.elementAt(workingDataNo)));
        }
        // now we have to parallelize
        else {

            Vector<DataSourceDescription> splitData = new Vector<DataSourceDescription>();
            Vector<DataSourceDescription> splitOutput = this.output.elementAt(workingDataNo).split(maxParallel);

            tasks.add(this.getSplitterTask(maxParallel, this.dataSets.elementAt(workingDataNo), splitData));

            for (int i = 0; i < maxParallel; ++i){
                tasks.add(new EvaluationDescription(this.id, this.algorithm, (DataSetDescription) splitData.elementAt(i),
                        inputFeats, (FileDescription) splitOutput.elementAt(i)));
            }

            tasks.add(this.getMergerTask(splitOutput, this.output.elementAt(workingDataNo)));
        }

        return tasks;
    }

    /**
     * Creates the {@link en_deep.mlprocess.manipulation.DataSplitter} task in order to split the given
     * data in the given number of parts.
     *
     * @param parts the number of data parts
     * @param data the data to be split
     * @param splitData the data parts' descriptions are added here
     * @return the task that splits the data
     */
    ManipulationDescription getSplitterTask(int parts, DataSetDescription data, Vector<DataSourceDescription> splitData){

        Vector<DataSourceDescription> allData = new Vector<DataSourceDescription>(1); // just formally create a vector for the input data set
        Vector<FeatureDescription> neededFeats = new Vector<FeatureDescription>();
        Vector<FeatureDescription> createdFeats = new Vector<FeatureDescription>();

        allData.add(data);
        splitData.addAll(data.split(parts));

        for (DataSourceDescription dsd : this.input){
            FeatureDescription fd = (FeatureDescription) dsd;
            neededFeats.add(new FeatureDescription(fd.id, data.id));
            for (DataSourceDescription part : splitData){
                createdFeats.add(new FeatureDescription(fd.id, ((DataSetDescription) part).id));
            }
        }

        return new ManipulationDescription(this.id,
                new AlgorithmDescription(DataSplitter.class.getCanonicalName(), ""),
                allData, splitData, neededFeats,  createdFeats);
    }


    /**
     * Creates the {@link en_deep.mlprocess.manipulation.DataMerger} task in order to merge the given
     * data into one part. May be used with split data sets or files: in each case this creates a different
     * class.
     *
     * @param splitData the data parts' descriptions
     * @param the resulting output data description
     * @throws DataException if the input and output data source types don't match
     * @return the task that splits the data
     */
    ManipulationDescription getMergerTask(Vector<DataSourceDescription> splitData, DataSourceDescription outData)
        throws DataException{

        boolean fileMode = (outData.type == DataSourceType.FILE);
        Vector<DataSourceDescription> allData = new Vector<DataSourceDescription>(1); // formally creating vector for output data
        Vector<FeatureDescription> createdFeats = new Vector<FeatureDescription>();
        Vector<FeatureDescription> neededFeats = new Vector<FeatureDescription>();

        for (DataSourceDescription dsd: splitData){ // this is probably not needed, just an assertion
            if ((fileMode && dsd.type != DataSourceType.FILE) || (dsd.type != DataSourceType.DATA_SET)){
                throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
            }
        }
        allData.add(outData);

        // add needed & created features, if operating on data sets (for computation tasks only,
        // the evaluation tasks operate on files only!)
        if (!fileMode){
            for (DataSourceDescription dsd : this.output){
                createdFeats.add(new FeatureDescription(((FeatureDescription) dsd).id, ((DataSetDescription)outData).id));
                for (DataSourceDescription part : splitData){
                    neededFeats.add(new FeatureDescription(((FeatureDescription) dsd).id, ((DataSetDescription)part).id));
                }
            }
        }

        return new ManipulationDescription(this.id,
                new AlgorithmDescription(fileMode ? FileMerger.class.getCanonicalName() : DataMerger.class.getCanonicalName(), ""),
                splitData, allData, neededFeats, createdFeats);
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
            case CREATED:
                if (this.type != TaskType.MANIPULATION){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.created = new Vector<FeatureDescription>();
                break;
            case NEEDED:
                if (this.type != TaskType.MANIPULATION){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.needed = new Vector<FeatureDescription>();
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
                if ((this.type == TaskType.COMPUTATION && (desc.type != DataSourceType.FEATURE || ((FeatureDescription)desc).dataSetId != null))
                        || (this.type == TaskType.MANIPULATION && (desc.type != DataSourceType.DATA_SET || desc.type != DataSourceType.FILE))
                        || (this.type == TaskType.EVALUATION && (desc.type != DataSourceType.FEATURE || ((FeatureDescription)desc).dataSetId != null))){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.input.add(desc);
                break;

            case OUTPUT: // TODO really allow only one file in Evaluation tasks ?
                if ((this.type == TaskType.COMPUTATION && (desc.type != DataSourceType.FEATURE || ((FeatureDescription)desc).dataSetId != null))
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

            case CREATED:
                if (desc.type != DataSourceType.FEATURE && ((FeatureDescription) desc).dataSetId == null){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.created.add((FeatureDescription)desc);
                break;

            case NEEDED:
                if (desc.type != DataSourceType.FEATURE && ((FeatureDescription) desc).dataSetId == null){
                    throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
                }
                this.needed.add((FeatureDescription)desc);
                break;


            default:
                throw new DataException(DataException.ERR_INVALID_DATA_TYPE);
        }
    }

}
