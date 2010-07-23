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

import en_deep.mlprocess.Process;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class unifies some of the functions needed for the classes that manipulate with ST files.
 *
 * @see ResultsToSt
 * @see StToArff
 * @author Ondrej Dusek
 */
public abstract class StManipulation extends Task {
    
    /* CONSTANTS */

    /** The lang_conf parameter name */
    static final String LANG_CONF = "lang_conf";
    /** The predicted parameter name */
    static final String PREDICTED = "predicted";

    /* DATA */

    /** The input reader */
    protected StReader reader;


    /* METHODS */

    /**
     * This creates the task, checking the parameters that are shared for both the St-manipulating tasks.
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    protected StManipulation(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException{

        super(id, parameters, input, output);

        // check the lang_conf parameter
        if (this.parameters.get(LANG_CONF) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter lang_conf is missing.");
        }

        // find the configuration file
        String configFile = StringUtils.getPath(this.parameters.get(LANG_CONF));
        if (!new File(configFile).exists()){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Cannot find config file.");
        }
        this.parameters.put(LANG_CONF, configFile); // set-up the LANG_CONF path

        // initialize the ST reader
        try {
            this.reader = new StReader(this);
        }
        catch (IOException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                    "Cannot initialize ST reader, probably lang_conf file error:" + e.getMessage());
        }
    }
}
