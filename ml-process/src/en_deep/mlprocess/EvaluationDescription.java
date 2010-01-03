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
public class EvaluationDescription extends TaskDescription {

    /* DATA */

    /* The working data set */
    private DataSetDescription data;

    /* The input features */
    private Vector<FeatureDescription> input;

    /* The output file */
    private FileDescription output;


    /* METHODS */

    /**
     * Creates a new {@link EvaluationDescription}, given all the needed data.
     *
     * @param idPrefix the prefix for the task ID generation
     * @param algorithm the description of the used algorithm
     * @param data description of the working data set
     * @param input description of the input feature(s)
     * @param output descritpion of the output file
     */
    public EvaluationDescription(String idPrefix, AlgorithmDescription algorithm,
            DataSetDescription data, Vector<FeatureDescription> input, FileDescription output) {

        super(TaskType.EVALUATION, idPrefix, algorithm);

        this.data = data;
        this.input = input;
        this.output = output;
    }

    @Override
    public Vector<DataSourceDescription> getInputDataSources() {
        
        Vector<DataSourceDescription> ids = new Vector<DataSourceDescription>();

        // add all the features for the input data set
        for (FeatureDescription feat : this.input){
            ids.add(new FeatureDescription(feat.id, this.data.id));
        }
        // add the working data set itself
        ids.add(this.data);

        return ids;
    }

    @Override
    public Vector<DataSourceDescription> getOutputDataSources() {

        Vector<DataSourceDescription> ods = new Vector<DataSourceDescription>(1);
        ods.add(this.output);
        return ods;
    }

}
