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

package en_deep.mlprocess.computation;

import en_deep.mlprocess.*;
import java.util.Vector;

/**
 * A computation (i.e\. learning &amp; classification) {@link en_deep.mlprocess.Task}.
 *
 * Operates with a given algorithm which is fed certain features from a data set and
 * computes other features. 
 *
 * @author Ondrej Dusek
 */
public abstract class Computation extends Task {

    /* DATA */

    /** The description of the training data set (or file to load the input from) */
    private DataSourceDescription train;
    /** The description of the evaluation data set (may be null) */
    private DataSourceDescription eval;
    /** The description of the testing data set (or file to save the output to) */
    private DataSourceDescription test;

    /** List of all input features */
    private Vector<FeatureDescription> input;
    /** List of all output features */
    private Vector<FeatureDescription> output;

    /* METHODS */
    
    /**
     * A constructor to be used with derived classes, just setting the input and output
     * data descriptions. This is the form of the constructor that all {@link Computation}
     * derived classes should have.
     *
     * @param id the id of this {@link Task}
     * @param params the class parameters
     */
    protected Computation(String id, String params,
            DataSourceDescription train, DataSourceDescription eval, DataSourceDescription test,
            Vector<FeatureDescription> input, Vector<FeatureDescription> output){

        super(id, params);

        this.input = input;
        this.output = output;
        this.train = train;
        this.eval = eval;
        this.test = test;
    }
}
