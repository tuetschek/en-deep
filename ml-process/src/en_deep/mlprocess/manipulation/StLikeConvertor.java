/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import en_deep.mlprocess.utils.StringUtils;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Abstract class containing some functions useful for conversion from ST-related formats to ARFF.
 * @author Ondrej Dusek
 */
public abstract class StLikeConvertor extends StManipulation {

    /* CONSTANTS */

    /** Start of the data section in ARFF files */
    private static final String DATA = "@DATA";
    /** Caption of ARFF files */
    protected static final String RELATION = "@RELATION";
    /** The header for the "file" attribute in ARFF files */
    private static final String FILE_ATTR_HEADER = "@ATTRIBUTE file STRING";

    /** The generate parameter name */
    private static final String GENERATE = "generate";


    /* DATA */

    /** Features to be generated */
    protected Vector<Feature> genFeats;

    /* METHODS */


    /**
     * This creates a new {@link StLikeConvertor} task. Should be used by the
     * derived classes only.
     * 
     * <strong>Parameters:</strong>
     * </p>
     * <ul>
     * <li><tt>generate</tt> -- comma-separated list of features to be generated</li>
     * <li><tt>omit_semclass</tt> -- if set to non-false, the semantic class is not output at all</li>
     * </ul>
     *
     */
    protected StLikeConvertor(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // initialize features to be generated
        this.initGenFeats();
    }

    /**
     * Performs the task operation -- converts all the given input file(s) according to the parameters.
     * @throws TaskException
     */
    @Override
    public abstract void perform() throws TaskException;

    /**
     * Parse the parameter with generated features setting and initialize all needed.
     */
    private void initGenFeats() {

        String[] featList;

        this.genFeats = new Vector<Feature>();
        if (this.parameters.get(GENERATE) == null) {
            return;
        }
        featList = this.parameters.get(GENERATE).split(",");
        for (String featName : featList) {
            Feature feat = Feature.createFeature(featName.trim(), this.reader);
            if (feat == null) {
                Logger.getInstance().message(this.id + ": Feature " + featName + " has not been found, skipping.",
                        Logger.V_WARNING);
            } else {
                this.genFeats.add(feat);
            }
        }
    }

    /**
     * This writes one ARFF file header with STRING fields into the given output stream.
     * @param out the output stream to write to
     * @param relationName the new ARFF relation name
     * @param fileAttr write the "file" attribute header (for one-file mode with multiple predicates)
     */
    protected void writeHeader(PrintStream out, String relationName, boolean fileAttr, boolean semClass) {
        out.println(StToArff.RELATION + " \"" + StringUtils.escape(relationName) + "\"");

        // print the "file" parameter, if in one-file mode
        if (fileAttr) {
            out.println(FILE_ATTR_HEADER);
        }
        out.println(this.reader.getArffHeaders());

        // print generated features' headers
        for (Feature f : this.genFeats) {
            out.println(f.getHeader());
        }
        // print semrel headers (if supposed to)
        if (semClass) {
            out.println(this.reader.getSemRolesHeader());
        }
        out.println(DATA);
    }

}
