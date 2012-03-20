/*
 *  Copyright (c) 2012 Ondrej Dusek
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
package en_deep.mlprocess.simple;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.computation.wekaclassifier.Model;
import en_deep.mlprocess.manipulation.AttributeFilter;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import weka.core.Instances;

/**
 * A classification settings container. 
 * 
 * Capable of saving / loading a heap of models + classification settings from / to a file.
 * @author Ondrej Dusek
 */
public class ClassificationSettings {
    
    /** Filtering information used by {@link AttributeFilter} */
    public Instances dataHeaders;
    
    /** All {@link Model}s used in the given classification setting */
    public Hashtable<String, Model> models;
    
    /** Class attribute name */
    public String classArg;
    
    /** Output classes only ? */
    public boolean classesOnly;
    /** Output probability distributions ? */
    public boolean probDist;
    /** Model selection attribute */
    public String modelSelAttr;
    
    /** An empty constructor */
    public ClassificationSettings(){
        this.models = new Hashtable<String, Model>();
    }
    
    /**
     * A constructor that will load all the settings from a file.
     * @param modelsFile the input file to be loaded
     */
    public ClassificationSettings(String modelsFile) throws IOException, ClassNotFoundException{
        this.load(modelsFile);
    }

    /** 
     * Save the whole conversion settings to a file.
     * 
     * @param outputFile name of the target output file
     */
    void save(String outputFile) throws IOException {

        Logger.getInstance().message("Saving models to " + outputFile + "...", Logger.V_DEBUG);
        ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(outputFile)));
        
        // write the settings
        out.writeBoolean(this.classesOnly);
        out.writeBoolean(this.probDist);

        out.writeObject(this.classArg);
        out.writeObject(this.modelSelAttr);
        out.writeObject(this.dataHeaders);
        
        // write the models
        String [] modelKeys = this.models.keySet().toArray(new String [0]);
        Arrays.sort(modelKeys);
        
        out.writeObject(modelKeys);
        
        for (String modelKey : modelKeys){
            this.models.get(modelKey).save(out);
        }
        
        out.close();
    }
    
    /**
     * Load the whole conversion settings from a file.
     * 
     * @param inputFile the name of the input file
     * @throws IOException 
     */
    void load(String inputFile) throws IOException, ClassNotFoundException {
        
        Logger.getInstance().message("Loading models from " + inputFile + "...", Logger.V_DEBUG);
        ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(inputFile)));
        
        // read settings
        this.classesOnly = in.readBoolean();
        this.probDist = in.readBoolean();
    
        this.classArg = (String) in.readObject();
        this.modelSelAttr = (String) in.readObject();
        this.dataHeaders = (Instances) in.readObject();
        
        // read list of models        
        String [] modelKeys = (String []) in.readObject();
        this.models = new Hashtable<String, Model>(modelKeys.length);
        
        for (String modelKey : modelKeys){
            
            Model model = new Model("PackedTask", inputFile);
            model.load(in);
            this.models.put(modelKey, model);
        }
        
    }
    
}
