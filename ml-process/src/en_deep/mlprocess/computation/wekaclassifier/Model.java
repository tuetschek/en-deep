/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package en_deep.mlprocess.computation.wekaclassifier;

import en_deep.mlprocess.Logger;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import weka.classifiers.AbstractClassifier;

/**
 * This comprises all the required fields for a classification model.
 */
/**
 *
 * @author odusek
 */
public class Model {
    /** The classifier */
    public AbstractClassifier classif;
    /** Attribute preselection setting */
    public int[] selectedAttributes;
    /** The attributes preselection mask: -1 for removed attributes, the actual position for others */
    public int[] attribsMask;
    /** The class attribute number */
    public int classAttrib;
    /** Binarize the data set for classification ? */
    public boolean binarize;
    /** The current task ID */
    private String taskId;
    /** The specified model file used for loading */
    private String modelFile;

    /** Default empty constructor */
    public Model() {
    }

    /**
     * This just prepares the model to be loaded using {@link #load() }.
     *
     * @param taskId used only for error messages
     * @param modelFile the name of the file that contains the model and other settings.
     */
    public Model(String taskId, String modelFile) {
        this.taskId = taskId;
        this.modelFile = modelFile;
    }

    /**
     * Initialize the used attributes mask (with the new positions of the used ones and -1 for unused ones).
     *
     * @param maxAttribs the number of attributes before feature pre-selection
     */
    public void initAttribsMask(int maxAttribs) {
        this.attribsMask = new int[maxAttribs];
        Arrays.fill(this.attribsMask, -1);
        for (int i = 0; i < this.selectedAttributes.length; ++i) {
            this.attribsMask[this.selectedAttributes[i]] = i;
        }
    }

    /**
     * This saves the trained classifier model to a file.
     *
     * @param taskId used only for error messages
     * @param the output file
     */
    public void save(String taskId, String modelFile) throws IOException {
        Logger.getInstance().message(taskId + ": saving the model to " + modelFile + " ...", Logger.V_DEBUG);
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(modelFile));
        this.save(out);
        out.close();
    }
    
    /**
     * Save the trained classifier model to an open {@link ObjectOutputStream}.
     *
     * @param out the open output stream
     */
    public void save(ObjectOutputStream out) throws IOException {
        
        out.writeObject(this.classif);
        out.writeObject(this.selectedAttributes);
        out.writeObject(new Integer(this.classAttrib));
        out.writeBoolean(this.binarize);
        out.writeObject(this.attribsMask);
    }

    /**
     * This loads the trained model from the given file. It also loads the attribute preselection settings
     * and the class attribute and binarization setting.
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void load() throws IOException, ClassNotFoundException {
        Logger.getInstance().message(taskId + ": loading the model from " + modelFile + " ...", Logger.V_DEBUG);
        ObjectInputStream oin = new ObjectInputStream(new FileInputStream(modelFile));
        this.load(oin);
        oin.close();
    }
    
    /**
     * This loads the trained model from an open {@link ObjectInputStream}.
     * @param oin The open {@link ObjectInputStream} to load the model from
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    public void load(ObjectInputStream oin) throws IOException, ClassNotFoundException {

        this.classif = (AbstractClassifier) oin.readObject();
        this.selectedAttributes = (int[]) oin.readObject();
        this.classAttrib = (Integer) oin.readObject();
        this.binarize = oin.readBoolean();
        this.attribsMask = (int[]) oin.readObject();
    }
    
}
