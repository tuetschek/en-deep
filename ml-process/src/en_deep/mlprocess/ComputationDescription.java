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
import java.util.Vector;

/**
 * A description for a {@link Computation} {@link Task}, containing all the necessary
 * metadata.
 * @author Ondrej Dusek
 */
public class ComputationDescription extends TaskDescription {

    /* DATA */

    /** Description of the training data for this task */
    private DataSourceDescription train;
    /** Description of the development data for this task */
    private DataSourceDescription devel;
    /** Description of the evaluation data for this task */
    private DataSourceDescription eval;

    /** Input features for this {@link Computation} task */
    private Vector<FeatureDescription> input;
    /** Output features for this {@link Computation} task */
    private Vector<FeatureDescription> output;

    /* METHODS */

    /**
     * Creates a new {@link Computation} task description, given all the needed data.
     * 
     * @param idPrefix the prefix for the task ID generation
     * @param algorithm the description of the used algorithm
     * @param train the training data description
     * @param devel the development data description
     * @param eval the evaluation data description
     * @param input the input features description
     * @param output the output features description
     */
    public ComputationDescription(String idPrefix, AlgorithmDescription algorithm,
            DataSourceDescription train, DataSourceDescription devel, DataSourceDescription eval,
            Vector<FeatureDescription> input, Vector<FeatureDescription> output){

        super(TaskType.COMPUTATION, idPrefix, algorithm);
        this.train = train;
        this.devel = devel;
        this.eval = eval;
        this.input = input;
        this.output = output;
    }

    @Override
    public Vector<DataSourceDescription> getInputDataSources() {

        Vector<DataSourceDescription> ids = new Vector<DataSourceDescription>();

        // add input features for all input data sets
        for (FeatureDescription feat : this.input){

            Vector<DataSourceDescription> allSets = new Vector<DataSourceDescription>();

            allSets.add(this.train);
            allSets.add(this.devel);
            allSets.add(this.eval);

            for (DataSourceDescription dsdDS : allSets){
                if (dsdDS.type != DataSourceType.DATA_SET){
                    continue;
                }
                DataSetDescription ds = (DataSetDescription) dsdDS;
                ids.add(new FeatureDescription(ds.id + "::" + feat.id));
            }
        }

        // add output features in input data sets as input features
        for (FeatureDescription feat : this.output){

            Vector<DataSourceDescription> inputSets = new Vector<DataSourceDescription>();

            inputSets.add(this.train);
            inputSets.add(this.devel);

            for (DataSourceDescription dsdDS : inputSets){
                if (dsdDS.type != DataSourceType.DATA_SET){
                    continue;
                }
                DataSetDescription ds = (DataSetDescription) dsdDS;
                ids.add(new FeatureDescription(ds.id + "::" + feat.id));
            }
        }

        // add the data sets themselves
        ids.add(this.train);
        ids.add(this.devel);
        // omit output file, if just writing output data (i.e. file is not used before)
        if (this.eval.type != DataSourceType.FILE){
            ids.add(this.eval);
        }

        return ids;
    }

    @Override
    public Vector<DataSourceDescription> getOutputDataSources() {

        Vector<DataSourceDescription> ods = new Vector<DataSourceDescription>();

        // add all output features in output data sets
        if (this.eval.type == DataSourceType.DATA_SET){

            for (FeatureDescription feat : this.output){
                DataSetDescription ds = (DataSetDescription) this.eval;
                ods.add(new FeatureDescription(ds.id + "::" + feat.id));
            }
        }
        // add the output file
        else {
            ods.add(this.eval);
        }

        return ods;
    }

}
