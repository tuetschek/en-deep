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

package en_deep.mlprocess.manipulation.genfeat;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Process;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.StToArff.StToArffConfig;
import en_deep.mlprocess.manipulation.StToArff;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class adds the number(s) of the clusters for the individual words to the list of
 * usable features. It needs an additional parameter to the {@link StToArff} class:
 * <ul>
 * <li><tt>clusters_file</tt> -- a space-separated list of filenames in the working directory that contain the
 *      cluster associations</li>
 * </ul>
 * The cluster file must contain the list of words in one cluster on each line.
 * @author Ondrej Dusek
 */
public class Clusters extends Feature {

    /* CONSTANTS */

    /** The cluster_file parameter name */
    private static final String CLUSTER_FILE = "cluster_file";
    /** Feature name prefix */
    private static final String FEAT_NAME_PREFIX = "Cluster_";

    /* DATA */
    
    /** The names of all the given cluster files */
    private String [] clusterFileNames;
    /** The names of the features (corresponding to {@link #clusterFileNames} */
    private String [] featNames;

    /** The cluster information variants for all words and all cluster files */
    private Hashtable<String, Integer> [] clusters;
    
    /* METHODS */


    /**
     * Just the initialization -- reads the cluster files and saves the clusters for later use.
     * @param config
     */
    public Clusters(StToArffConfig config) throws TaskException{

        super(config);

        if (config.getTaskParameter(CLUSTER_FILE) == null){
            Logger.getInstance().message(config.getTaskId() + " : Cluster feature not initialized.", Logger.V_IMPORTANT);
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, config.getTaskId());
        }
        this.clusterFileNames = config.getTaskParameter(CLUSTER_FILE).split("\\s+");
        this.featNames = new String [this.clusterFileNames.length];

        for (int i = 0; i < this.clusterFileNames.length; ++i){

            this.featNames[i] = FEAT_NAME_PREFIX + this.clusterFileNames[i].replaceAll("[^a-z0-9]", "");
            this.clusterFileNames[i] = Process.getInstance().getWorkDir() + this.clusterFileNames[i];
        }

        this.clusters = new Hashtable[this.clusterFileNames.length];
        for (int i = 0; i < this.clusterFileNames.length; ++i){
            this.clusters[i] = this.loadClusters(this.clusterFileNames[i]);
        }
    }

    @Override
    public String getHeader() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String generate(Vector<String[]> sentence, int wordNo, int predNo) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private Hashtable<String, Integer> loadClusters(String string) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
