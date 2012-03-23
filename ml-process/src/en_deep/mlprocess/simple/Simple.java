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
import en_deep.mlprocess.computation.WekaClassifier;
import en_deep.mlprocess.exception.ParamException;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.AttributeFilter;
import en_deep.mlprocess.utils.StringUtils;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SparseInstance;


/**
 * The main program for running a simple classification as a Unix filter (reading ARFF without headers
 * from the input and writing ARFF without headers on the output). 
 * 
 * It checks the command parameters, loads the given models and awaits data on the standard input.
 * 
 * @author Ondrej Dusek
 */
public class Simple {
    
    /* CONSTANTS */
    
    /** Task name for error messages */
    private static final String TASK_NAME = "SimpleClassif";

    /** A replacement value for filtered-out values */
    private static final String OTHER_VAL = AttributeFilter.OTHER_VALUE;
    
    /** Name of the chunk_attr parameter */
    private static final String OPTL_CHUNK_ATTR = "chunk_attr";
    /** Shortcut of the chunk_attr parameter */    
    private static final char OPTS_CHUNK_ATTR = 'a';
    
    /** Name of the chunk_size parameter */        
    private static final String OPTL_CHUNK_SIZE = "chunk_size";
    /** Shortcut of the chunk_size parameter */        
    private static final char OPTS_CHUNK_SIZE = 's';
    
    /** The --verbosity option long name */
    private static final String OPTL_VERBOSITY = "verbosity";
    /** The --verbosity option short name */
    private static final char OPTS_VERBOSITY = 'v';
    
    /** The --charset option long name */
    private static final String OPTL_CHARSET = "charset";
    /** The --charset option short name */
    private static final char OPTS_CHARSET = 'c';
    
    /** The --signal_ready option long name */
    private static final String OPTL_SIGNAL_READY = "signal_ready";
    /** The --signal_ready short name */
    private static final char OPTS_SIGNAL_READY = 'r';
    
    /** Default chunking size */
    private static final int DEFAULT_CHUNK_SIZE = 10;
    
    /** Basic help string */
    private static final String USAGE = "Usage:\n\tjava -cp ml-process.jar en_deep.mlprocess.simple.Simple\n\t"
            + "[-s num|-a attribute_name] [-c charset] [-v verbosity] [-r]\n\tmodels.dat.gz < input > output\n\n";
    

    /** Program name as it's passed to getopts */
    private static final String PROGNAME = "ML-Process_simple";

    /** Optstring for getopts, must correspond to the OPTS_ constants */
    private static final String OPTSTRING = "a:s:v:c:r";

    /* DATA */
    
    /** The used classifier */
    WekaClassifier classif;
    
    /** Input reader */
    Scanner input;
    
    /** The processing options */
    SimpleRunOptions opts;

    
    
    /* METHODS */

    
    /**
     * The main program entry. Possible command arguments:
     * <ul>
     * <li><tt>--charset|-c</tt> -- Character set used for communication (Java character set names, default: UTF-8).</li>
     * <li><tt>--verbosity|-v</tt> -- Verbosity setting (0-4, default: 1).</li>
     * <li><tt>--chunk_size|-s</tt> -- Size of chunks fed to the classifier (default: 10, set to 1 for instant responses).</li>
     * <li><tt>--chunk_attr|-c</tt> -- Control the chunks fed to the classifier by a change in the value of one of
     * the attributes (specify the name here; this is an alternative to the <tt>-s</tt> option).</li>
     * <li><tt>--signal_ready|-r</tt> -- Output <tt>READY</tt> on the first line when the classifiers are loaded.</li>
     * </ul>
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        
        try {
            
            LongOpt[] possibleOpts = new LongOpt[4];
            possibleOpts[0] = new LongOpt(OPTL_CHUNK_ATTR, LongOpt.REQUIRED_ARGUMENT, null, OPTS_CHUNK_ATTR);
            possibleOpts[1] = new LongOpt(OPTL_CHUNK_SIZE, LongOpt.REQUIRED_ARGUMENT, null, OPTS_CHUNK_SIZE);
            possibleOpts[2] = new LongOpt(OPTL_VERBOSITY, LongOpt.REQUIRED_ARGUMENT, null, OPTS_VERBOSITY);
            possibleOpts[3] = new LongOpt(OPTL_CHARSET, LongOpt.REQUIRED_ARGUMENT, null, OPTS_CHARSET);
            
            Getopt getter = new Getopt(PROGNAME, args, OPTSTRING, possibleOpts);
            int c;
            getter.setOpterr(false);
            SimpleRunOptions opts = new SimpleRunOptions();
            

            while ((c = getter.getopt()) != -1) {
                switch (c) {
                    case OPTS_CHUNK_ATTR:
                        opts.chunkAttr = getter.getOptarg();
                        break;
                    case OPTS_CHUNK_SIZE:
                        opts.chunkSize = StringUtils.getNumericArgPar(OPTL_CHUNK_SIZE, getter.getOptarg());
                        break;
                    case OPTS_VERBOSITY:
                        Logger.getInstance().setVerbosity(StringUtils.getNumericArgPar(OPTL_VERBOSITY, getter.getOptarg()));                        
                        break;
                    case OPTS_CHARSET:
                        opts.charset = getter.getOptarg();                        
                        break;
                    case OPTS_SIGNAL_READY:
                        opts.signalReady = true;
                        break;
                    case ':':
                        throw new ParamException(ParamException.ERR_MISSING, "" + (char) getter.getOptopt());
                    case '?':
                        throw new ParamException(ParamException.ERR_INVPAR, "" + (char) getter.getOptopt());
                }
            }
            
            // checking if there are some parameters (model files) left
            if (getter.getOptind() > args.length - 1) {
                throw new ParamException(ParamException.ERR_MISSING, "models data file");
            }
            else if (getter.getOptind() < args.length - 1){
                throw new ParamException(ParamException.ERR_TOO_MANY);
            }
            opts.modelFile = args[getter.getOptind()];

            // check if chunkAttr and chunkSize are not set at the same time
            if (opts.chunkAttr != null && opts.chunkSize != 0){
                throw new ParamException(ParamException.ERR_INV_COMBINATION, "chunk_attr + chunk_size");
            }
            if (opts.chunkAttr == null && opts.chunkSize == 0){
                opts.chunkSize = DEFAULT_CHUNK_SIZE;
            }

            // run the conversion itself
            Simple main = new Simple();
            main.run(opts);
        }
        catch (ParamException e){
            Logger.getInstance().message(e.getMessage(), Logger.V_IMPORTANT);
            e.printStackTrace();
            System.err.print(USAGE);
            System.exit(1);                            
        }
        catch (Exception e){
            Logger.getInstance().message(e.getMessage(), Logger.V_IMPORTANT);
            e.printStackTrace();
            System.exit(1);            
        }
        
    }

    /**
     * The main method, loading the models and feeding in the input according to the given caching 
     * settings.
     * @param opts The processing options
     */
    private void run(SimpleRunOptions opts) throws Exception {
        
        // load the models + settings
        this.opts = opts;
        ClassificationSettings settings = new ClassificationSettings(this.opts.modelFile);
        this.classif = new WekaClassifier(TASK_NAME, settings);

        Instances toClassif = new Instances(settings.dataHeaders, 0);
        int ctr = 0;
        
        Logger.getInstance().message("Ready.", Logger.V_DEBUG);
        if (this.opts.signalReady){
            System.out.println("READY");
        }
        
        // read input, modify it to comply with the current headers, classify it
        while (true){
            String line = this.read();
            ctr++;
            
            // always flush output on empty line or a the end of input
            if (line == null || line.equals("")){                 
                
                if (toClassif.numInstances() > 0){
                    this.haveClassifiedAndPrint(toClassif);
                    toClassif.clear();
                }
                // return if there's nothing more to process
                if (line == null){
                    break;
                }
            }
            
            this.addInstance(toClassif, line);
            // flush according to cache size
            if ((this.opts.chunkSize > 0) && (ctr % this.opts.chunkSize == 0) ){
                this.haveClassifiedAndPrint(toClassif);
                toClassif.clear();
            }    
            // flush, given a different attribute value (keep the last/changed instance to the next chunk)
            if (this.opts.chunkAttr != null && toClassif.size() >= 2 
                    && toClassif.get(toClassif.size() - 2).value(toClassif.attribute(this.opts.chunkAttr)) 
                    != toClassif.get(toClassif.size() - 1).value(toClassif.attribute(this.opts.chunkAttr))){
                
                Instances last = new Instances(toClassif, toClassif.size() - 1, 1);
                toClassif.delete(toClassif.size() - 1);
                this.haveClassifiedAndPrint(toClassif);
                toClassif = last;
            }
        }
    }

    /**
     * Read the next line of input.
     * @return the next line read, or null if there's no line of input
     */
    private String read() {
               
        if (this.input == null){
            String charset = this.opts.charset != null ? this.opts.charset : Charset.defaultCharset().name();
            this.input = new Scanner(System.in, charset);
        }
        if (this.input.hasNextLine()){
            return this.input.nextLine();
        }
        return null;
    }

    /**
     * Parse the given input string and add it to the given set. Throw an exception if the
     * line does not conform to the data format prescribed by the data set.
     * 
     * @param dataSet the data set the next input line should be appended to
     * @param instLine the input string (to be recognized as an instance belonging to the current data set)
     * @throws TaskException if the data format of the given line is not recognized
     */
    private void addInstance(Instances dataSet, String instLine) throws TaskException {
        
        instLine.trim();
        Instance inst = null;
        
        // initial sparse/dense decision
        if (instLine.startsWith("{")){
            inst = new SparseInstance(1.0, new double [0], new int [0], dataSet.numAttributes());
        }
        else {
            inst = new DenseInstance(dataSet.numAttributes());
        }
        // the data set will make a copy, so fill the attributes of this copy
        dataSet.add(inst);        
        inst = dataSet.lastInstance(); 
        
        // now read all attributes and set their values, if applicable (incompatible values will be ignored)
        if (instLine.startsWith("{")){
            this.fillSparseAttributes(instLine, inst);
        }
        else {
            this.fillDenseAttributes(instLine, inst);
        }
        
    }

    /**
     * Haves the loaded classifier {@link Simple#classif} classify the given instances and prints out the
     * result in the ARFF headerless data format. The actual format (i.e. probability distribution, presence
     * of all attributes or just the class etc.) depends on the {@link Simple#opts} options.
     * 
     * @param toClassif the instances to be classified
     * @throws Exception if there's a classification error
     */
    private void haveClassifiedAndPrint(Instances toClassif) throws Exception {
                
        Instances results = classif.classifyInstances(toClassif);
        
        Enumeration<Instance> resultEnum = results.enumerateInstances();
        while (resultEnum.hasMoreElements()){
            Instance result = resultEnum.nextElement();
            System.out.println(result.toString());
        }
    }

    /**
     * Handle a value field for an instance, accounting for missing values etc.
     * 
     * @param inst the target instance
     * @param attrNum the attribute number of the current field
     * @param field the ARFF string value of the field
     */
    private void handleField(Instance inst, int attrNum, String field){

        if (field.equals("?")){ // missing value
            inst.setMissing(attrNum);
        }
        else if (field.matches("['\"].*['\"]")){  // quoted value
            field = field.substring(1, field.length()-1);         // unquote
            field.replaceAll("\\\\([\\n\\r'\"\\\\\\t%])", "$1");  // unescape
            this.assignValue(inst, attrNum, field);
        }
        else {
            field.trim();
            this.assignValue(inst, attrNum, field);
        }            

    }
    
    /**
     * Parse a sparse instance line, filling all the present attributes into the 
     * given {@link Instance}.
     * 
     * @param instLine the input ARFF instance line
     * @param inst the target instance to be filled
     * @throws TaskException if the input line does not match the number of attributes in the target instance
     */
    private void fillSparseAttributes(String instLine, Instance inst) throws TaskException {

        instLine = instLine.substring(1, instLine.length()-1) + ",";
        
        Pattern attr = Pattern.compile("([0-9]+)\\s+([^\"'\\s][^,]*|'[^']*(\\\\'[^']*)*'|\"[^\"]*(\\\\\"[^\"]*)*\"),");
        Matcher lineMatch = attr.matcher(instLine);

        while (lineMatch.find()){
                        
            int attrNum = Integer.parseInt(lineMatch.group(1));
            String field = lineMatch.group(2);

            if (inst.numAttributes() <= attrNum){
                throw new TaskException(TaskException.ERR_INVALID_DATA, TASK_NAME, "Invalid sparse attribute number: " + attrNum);
            }
            
            this.handleField(inst, attrNum, field);
        }                
    }
            

    /**
     * Parse a dense instance line, filling all the attributes read into the 
     * given {@link Instance}.
     * 
     * @param instLine the input ARFF instance line
     * @param inst the target instance to be filled
     * @throws TaskException if the input line does not match the number of attributes in the target instance
     */
    private void fillDenseAttributes(String instLine, Instance inst) throws TaskException {

        int numValues = 0;
        instLine += ",";
        
        Pattern attr = Pattern.compile("([^\"'][^,]*|'[^']*(\\\\'[^']*)*'|\"[^\"]*(\\\\\"[^\"]*)*\"),");
        Matcher lineMatch = attr.matcher(instLine);
                
        while (lineMatch.find()) {

            String field = lineMatch.group(1);
            
            this.handleField(inst, numValues, field);
            numValues++;
        }
        if (numValues != inst.numAttributes()){
            throw new TaskException(TaskException.ERR_INVALID_DATA, TASK_NAME, "Invalid number of attributes: " 
                    + numValues + "(expected " + inst.numAttributes() + ")");
        }        
    }

    /**
     * Assign the given string value to the given target instance at the given attribute number.
     * The process depends on the target attribute type: If the attribute is numeric, a number is parsed
     * (and possibly an exception raised), if the attribute is a string, the value is just set (possibly
     * adding a new value to the list) and if the attribute is nominal, it is left unset if the string
     * value does not match any of the possible values.
     * 
     * @param inst the target instance
     * @param attrNum the target attribute number
     * @param field the ARFF string field value
     * @todo make it faster by avoiding setValue (i.e. preparing the values elsewhere)
     */
    private void assignValue(Instance inst, int attrNum, String field) {
        
        switch(inst.attribute(attrNum).type()){
            
            case Attribute.NUMERIC:
                inst.setValue(attrNum, Double.parseDouble(field));
                break;
                
            case Attribute.NOMINAL:
                int idx = inst.attribute(attrNum).indexOfValue(field);
                // try the actual value first
                if (idx >= 0){
                    inst.setValue(attrNum, idx);
                }
                // try to fill in the replacement value for filtered-out values
                else if ((idx = inst.attribute(attrNum).indexOfValue(OTHER_VAL)) >= 0){ 
                    inst.setValue(attrNum, idx);                    
                }
                // if not even the replacement is found, set the value to be missing
                else {
                    inst.setMissing(attrNum);
                }
                break;
                
            case Attribute.STRING:
                inst.setValue(attrNum, field);
                break;
                
            default:
                throw new UnsupportedOperationException("Unsupported attribute type (" + inst.attribute(attrNum).name() + ")");
        }
    }
    
    
    
    /**
     * Just a container for the various processing options.
     */
    private static class SimpleRunOptions {

        /** Filtering by attribute value change */
        String chunkAttr;
        
        /** Filter cache size */
        int chunkSize;
        
        /** The file with the loaded models */
        String modelFile;
        
        /** The charset to be used during the processing */
        String charset;
        
        /** Signal 'READY' on the output */
        boolean signalReady;
    }
    
}


