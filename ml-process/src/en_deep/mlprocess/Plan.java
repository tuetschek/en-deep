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

import en_deep.mlprocess.exception.DataException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Vector;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * This component is responsible for building the planFile of the whole computation,
 * according to the input scenario.
 *
 * @author Ondrej Dusek
 */
public class Plan {


    /* DATA */

    /** The planFile file */
    private File planFile;

    /** The only instance of {@link Plan}. */
    private static Plan instance = null;

    /** The tasks and their features and pending statuses */
    private Vector<TaskSection> tasks;

    /* METHODS */

    /**
     * Creates a new instance of {@link Plan}. All objects should call
     * {@link Plan.getInstance()} to acquire an instance of {@link Plan}.
     */
    private Plan(){
        
        // open the planFile file (and create it if necessary)
        this.planFile = new File(Process.getInstance().getInputFile() + ".todo");

        try {
            // this ensures we never have an exception, but the file may be empty
            planFile.createNewFile();
        }
        catch(IOException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
        }
    }

    /**
     * Retrieves the only instance of the {@link Plan} singleton. This calls
     * the {@link Plan} constructor upon first call.
     *
     * @return the only instance of {@link Plan}
     */
    public Plan getInstance(){

        if (Plan.instance == null){
            Plan.instance = new Plan();
        }
        return Plan.instance;
    }


    /**
     * Tries to get the next pending task from the to-do file.
     * <p>
     * Locks the to-do file
     * to avoid concurrent access from within several instances. If the to-do file does
     * not exist or is empty, creates it and fills it with a planFile.
     * </p><p>
     * Returns null in case of an error or nothing else to do. All errors are logged with
     * the highest importance setting.
     * <p>
     *
     * @return the next pending task to be done, or null if there are no tasks to be done (or an
     *         error occurred)
     */
    public synchronized Task getNextPendingTask() {

        FileLock lock = null;
        Task nextPending = null;
        
        // try to acquire lock on the to-do file and get a planned task
        try {
            RandomAccessFile planFileIO = new RandomAccessFile(this.planFile, "rw");
            lock = planFileIO.getChannel().lock();

            if (planFileIO.length() == 0){ // the planFile file - the planFile has not yet been created
                this.createPlan(planFileIO);
            }

            nextPending = this.getNextPendingTask(planFileIO);
        }
        catch(IOException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
            return null;
        }
        catch(SAXException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
            return null;
        }
        catch(DataException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
            return null;
        }

        // always releas the lock on the to-do file
        finally {
            if (lock != null && lock.isValid()){
                try {
                    lock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    return null;
                }
            }
        }

        return nextPending;
    }

    /**
     * Creates the process planFile, so that {@link Worker}s may retrieve pending {@link Task}s
     * later.
     * Tries to read the process description XML file and create the to-do file according to
     * it, using DAG and parallelizations (up to the specified number of {@Worker}s for all
     * instances of the {@link Process}.
     *
     * @param planFileIO the to-do file, locked and opened for writing
     * @throws SAXException if the input XML file is invalid
     * @throws IOException if there are some I/O problems with the file
     * @throws DataException if there are some illogical event dependencies
     */
    private void createPlan(RandomAccessFile planFileIO) throws SAXException, IOException, DataException {

        Process process = Process.getInstance();
        XMLReader parser;
        ScenarioParser dataCollector = new ScenarioParser(process.getInputFile());
        Vector<TaskDescription> plan;

        parser = XMLReaderFactory.createXMLReader();
        parser.setContentHandler(dataCollector);
        parser.parse(process.getInputFile());

        // nejdřív zjistit závislosti, pak paralelizovat, pak teprve uspořádat do DAGu a podle toho zapsat.
        plan = dataCollector.tasks;
        this.setDependencies(dataCollector);

        // TODO parallelize! + write into planFileIO!
        // paralelizace je vlastně vložení pár Tasků do grafu, tj. neovlivňuje vstup/ výstup z 1 bodu, jen
        // se přesune výstup do jiného
        // uspořádání - Topological sorting (Wiki)
        
    }

    /**
     * Reads the to-do file structure and retrieves the next pending {@link Task}, updating its
     * progress status.
     *
     * @param planFileIO the to-do file, locked and opened for writing
     * @return the next pending task from the .todo file
     */
    private Task getNextPendingTask(RandomAccessFile planFileIO) {

        // TODO samotne nacteni DAGu tasku ze souboru (hlavne jmen, spojnic a stavu provadeni)
        // nic jineho by asi nebylo potreba, kdyz se bude XML jen cist ... ale zas uz bude rozparsovane,
        // tak mozna lepsi tam nacpat vsechna metadata.
        // DAG by mel mit format, kterym se nactou {@link TaskSection} a z nich se pomoci {@link TaskFactory}
        // bude dat stvorit skutecny {@link Task}. TaskSection musi obsahovat id primych predchudcu a nasledniku
        // - podle pointru by z nich mel jit autom. sestavit graf
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Set the dependencies according to features and data sets in the data itself and check them.
     * <p>
     * Check if all the data sets are correctly loaded or created in a {@link Manipulation} task.
     * All the features that are not computed are assumed to be contained in the input data sets.
     * All the files that are not written as output are assumed to exist before the {@link Process}
     * begins.
     * </p>
     *
     * @param plan the {@link TaskSection} as given by the {@link ScenarioParser}
     * @param parserOutput the {@link ScenarioParser} object <i>after</i> the parsing is finished
     */
    private void setDependencies(ScenarioParser parserOutput) throws DataException {

        // set the data set dependencies (check for non-created data sets)
        for (Occurrences oc : parserOutput.dataSetOccurrences.values()){
            if (oc.asOutput == null){
                throw new DataException(DataException.ERR_DATA_SET_NEVER_PRODUCED);
            }
            for (TaskDescription dep : oc.asInput){
                dep.setDependency(oc.asOutput);
            }
        }
        // set-up the file dependencies (no checks)
        for (Occurrences oc : parserOutput.fileOccurrences.values()){
            if (oc.asOutput == null){
                continue;
            }
            for (TaskDescription dep : oc.asInput){
                dep.setDependency(oc.asOutput);
            }
        }
        // set-up the feature-level dependencies (no checks)
        for (Occurrences oc : parserOutput.featureOccurrences.values()){
            if (oc.asInput == null){
                continue;
            }
            for (TaskDescription dep : oc.asInput){
                dep.setDependency(oc.asOutput);
            }
        }
    }
}
