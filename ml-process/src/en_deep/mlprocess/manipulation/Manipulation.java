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

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.DataSourceDescription;
import en_deep.mlprocess.Task;
import java.util.Vector;

/**
 * A data manipulation {@link en_deep.mlprocess.Task}.
 *
 * Operates with datasets - takes one or more data set and creates new one(s) by
 * filtering, splitting etc.
 *
 * @author Ondrej Dusek
 */
public abstract class Manipulation extends Task {

    /* DATA */

    /** All the input data sources descriptions */
    private final Vector<DataSourceDescription> input;
    /** All the output data sources descriptions */
    private final Vector<DataSourceDescription> output;
    
    /* METHODS */

    /**
     * A constructor for the Manipulation tasks' base class, just setting the input
     * and output variables.
     * This is the form of the constructor that all Manipulation classes should have.
     *
     * @param params parameters given to the class
     * @param input the input data sources
     * @param output the output data sources
     */
    protected Manipulation(String params, Vector<DataSourceDescription> input, Vector<DataSourceDescription> output){

        this.input = input;
        this.output = output;
    }
}
