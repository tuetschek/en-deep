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
import en_deep.mlprocess.exception.ParamException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A class that reads classification settings and a set of different models and packs them into
 * one file.
 * 
 * Usage:
 * <pre>
 * java -cp ml-process.jar en_deep.mlprocess.simple.PackModels [-f filtering_information] [-n] -p pattern \
 *      -c class [-o] -t output [-i ignore_attr] model-xxx.dat [model-yyy.dat ...]
 * </pre>
 * 
 * @author odusek
 */
public class PackModels {

    /* CONSTANTS */
    
    /** The --headers option long name */
    private static final String OPTL_HEADERS = "headers";
    /** The --headers option short name */
    private static final char OPTS_HEADERS = 'h';

    /** The --pattern option long name */
    private static final String OPTL_MODEL_PATTERN = "model_pattern";
    /** The --pattern option short name */
    private static final char OPTS_MODEL_PATTERN = 'm';
    
    /** The --class option long name */
    private static final String OPTL_CLASS_ARG = "class";
    /** The --class option short name */
    private static final char OPTS_CLASS_ARG = 'c';

    /** The --class_only option long name */
    private static final String OPTL_CLASS_ONLY = "class_only";
    /** The --class_only option short name */
    private static final char OPTS_CLASS_ONLY = 'o';
    
    /** The --to option long name */
    private static final String OPTL_OUTPUT = "to";
    /** The --to option short name */
    private static final char OPTS_OUTPUT = 't';

    /** The --ignore option long name */
    private static final String OPTL_PROB_DIST = "prob_dist";
    /** The --ignore option short name */
    private static final char OPTS_PROB_DIST = 'p';
    
    /** The --verbosity option long name */
    private static final String OPTL_VERBOSITY = "verbosity";
    /** The --verbosity option short name */
    private static final char OPTS_VERBOSITY = 'v';

    /** The --model_sel_attr option long name */
    private static final String OPTL_MODEL_SEL_ATTR = "model_sel_attr";
    /** The --model_sel_attr option short name */
    private static final char OPTS_MODEL_SEL_ATTR = 'a';

    /** Program name as it's passed to getopts */
    private static final String PROGNAME = "PackModels";

    /** Optstring for getopts, must correspond to the OPTS_ constants */
    private static final String OPTSTRING = "a:h:c:m:t:v:op";

    private static final String USAGE = "Usage:\n\tjava -cp ml-process.jar en_deep.mlprocess.simple.PackModels "
            + "-h data_headers_file -m model_pattern -a model_sel_attr\n\t-c class -t to_output_file [-o] [-p]\n\t"
            + "model-xxx.dat [model-yyy.dat ...]\n\n";
    
    /* DATA */
    
    /* METHODS */
    
    /**
     * Application main entry point, parses arguments and launches {@link PackModels#run() }.
     * @param args command-line arguments
     * @todo charset, verbosity
     */
    public static void main(String[] args) {

        try {
    
            LongOpt[] possibleOpts = new LongOpt[6];
            possibleOpts[0] = new LongOpt(OPTL_HEADERS, LongOpt.REQUIRED_ARGUMENT, null, OPTS_HEADERS);
            possibleOpts[1] = new LongOpt(OPTL_MODEL_PATTERN, LongOpt.REQUIRED_ARGUMENT, null, OPTS_MODEL_PATTERN);
            possibleOpts[2] = new LongOpt(OPTL_CLASS_ARG, LongOpt.REQUIRED_ARGUMENT, null, OPTS_CLASS_ARG);
            possibleOpts[3] = new LongOpt(OPTL_CLASS_ONLY, LongOpt.NO_ARGUMENT, null, OPTS_CLASS_ONLY);
            possibleOpts[4] = new LongOpt(OPTL_PROB_DIST, LongOpt.REQUIRED_ARGUMENT, null, OPTS_PROB_DIST);
            possibleOpts[5] = new LongOpt(OPTL_VERBOSITY, LongOpt.REQUIRED_ARGUMENT, null, OPTS_VERBOSITY);

            Getopt getter = new Getopt(PROGNAME, args, OPTSTRING, possibleOpts);
            PackOptions opts = new PackOptions();
            int c;
            getter.setOpterr(false);            

            while ((c = getter.getopt()) != -1) {
                switch (c) {
                    case OPTS_HEADERS:
                        opts.headers = getter.getOptarg();
                        break;
                    case OPTS_MODEL_PATTERN:
                        opts.modelPattern = getter.getOptarg();
                        break;
                    case OPTS_CLASS_ARG:
                        opts.classArg = getter.getOptarg();
                        break;
                    case OPTS_CLASS_ONLY:
                        opts.classesOnly = true;
                        break;
                    case OPTS_PROB_DIST:
                        opts.probDist = true;
                        break;
                    case OPTS_OUTPUT:
                        opts.outputFile = getter.getOptarg();
                        break;
                    case OPTS_MODEL_SEL_ATTR:
                        opts.modelSelAttr = getter.getOptarg();
                        break;
                    case OPTS_VERBOSITY:
                        Logger.getInstance().setVerbosity(StringUtils.getNumericArgPar(OPTL_VERBOSITY, getter.getOptarg()));
                        break;
                    case ':':
                        throw new ParamException(ParamException.ERR_MISSING, "" + (char) getter.getOptopt());
                    case '?':
                        throw new ParamException(ParamException.ERR_INVPAR, "" + (char) getter.getOptopt());
                }
            }

            // checking if there are some parameters (model files) left
            if (getter.getOptind() > args.length - 1) {
                throw new ParamException(ParamException.ERR_MISSING, "model data files list");
            }
            // checking if the required parameters have been set
            if (opts.modelPattern == null || opts.classArg == null || opts.outputFile == null || opts.headers == null 
                    || opts.modelSelAttr == null){
                throw new ParamException(ParamException.ERR_MISSING, "required parameters (model pattern, "
                        + "class attribute, output file, data headers, model selection attribute)");
            }
            // handle all model files
            for (int i = getter.getOptind(); i < args.length; ++i){
                opts.modelFiles.add(args[i]);
            }

            // run the conversion itself
            PackModels main = new PackModels();
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
     * The model packing process itself (main method).
     * 
     * @todo More exception checking
     */
    private void run(PackOptions opts) throws IOException, ParamException, ClassNotFoundException, Exception {
        
        ClassificationSettings cls = new ClassificationSettings();
        
        // directly copy some attributes
        cls.classArg = opts.classArg;
        cls.classesOnly = opts.classesOnly;
        cls.probDist = opts.probDist;
        cls.modelSelAttr = opts.modelSelAttr;

        // load filtering information
        cls.dataHeaders = FileUtils.readArffStructure(opts.headers);

        // load the individual models
        for (String modelFile : opts.modelFiles){

            // determine model name
            String name = StringUtils.matches(modelFile, opts.modelPattern);
            if (name == null){
                throw new ParamException(ParamException.ERR_INVPAR, "file " + modelFile 
                        + " does not match the pattern " + opts.modelPattern);
            }
            // load the model
            Model model = new Model("PackModels", modelFile);
            model.load();
            
            // save it to the settings
            cls.models.put(name, model);
        }
        
        cls.save(opts.outputFile);
    }

    private static class PackOptions {

        /** Is nominalization via {@link BigDataSplitter} needed ? */
        boolean nominalize;
        /** File path to ARFF data headers */
        String headers;
        /** Pattern for finding model names in file names */
        String modelPattern;
        /** Class attribute name */
        String classArg;
        /** Output classes only ? */
        boolean classesOnly;
        /** List of files with models */
        ArrayList<String> modelFiles = new ArrayList<String>();
        /** The output file */
        String outputFile;
        /** Output probability distribution ? */
        boolean probDist;
        /** Model selection attribute name */
        String modelSelAttr;
    }

}
