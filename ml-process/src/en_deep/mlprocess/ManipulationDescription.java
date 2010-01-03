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

import en_deep.mlprocess.Task.TaskType;
import java.util.Vector;

/**
 *
 * @author Ondrej Dusek
 */
public class ManipulationDescription extends TaskDescription {

    /* DATA */
    
    /** All the input data sets or files */
    Vector<DataSourceDescription> input;
    
    /** All the output data sets or files */
    Vector<DataSourceDescription> output;

    /** All the features that are needed to perform this manipulation task */
    Vector<FeatureDescription> needed;
    /** All the features that are created in this manipulation task (if the input and output data sets are the same) */
    Vector<FeatureDescription> created;


    /* METHODS */

    /**
     * Creates a new ManipulationDescription, given all the needed data.
     *
     * @param idPrefix the prefix for the task ID generation
     * @param algorithm the description of the used algorithm
     * @param input description of the input data or files
     * @param output description of the output data sets or files
     */
    public ManipulationDescription(String idPrefix, AlgorithmDescription algorithm,
            Vector<DataSourceDescription> input, Vector<DataSourceDescription> output,
            Vector<FeatureDescription> needed, Vector<FeatureDescription> created) {

        super(TaskType.MANIPULATION, idPrefix, algorithm);
        this.input = input;
        this.output = output;
        this.needed = needed;
        this.created = created;
    }
    

    @Override
    public Vector<DataSourceDescription> getInputDataSources() {

        Vector<DataSourceDescription> inputSources = new Vector<DataSourceDescription>();

        // add all input data sources
        inputSources.addAll(this.input);
        
        // add all required features
        if (this.needed != null){
            for (FeatureDescription fd : this.needed){
                inputSources.add(fd);
            }
        }

        return inputSources;
    }

    @Override
    public Vector<DataSourceDescription> getOutputDataSources() {

        Vector<DataSourceDescription> outputSources = new Vector<DataSourceDescription>();

        // add only newly created data sources - not the changed ones
        for (DataSourceDescription dsd: this.output){
            if (!this.input.contains(dsd)){
                this.output.add(dsd);
            }
        }

        // add all changes in the data sources
        if (this.created != null){
            for (FeatureDescription fd : this.created){
                outputSources.add(fd);
            }
        }

        return outputSources;
    }

}
