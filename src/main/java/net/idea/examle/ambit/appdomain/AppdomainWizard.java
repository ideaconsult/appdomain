package net.idea.examle.ambit.appdomain;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.idea.examle.ambit.appdomain.MainApp._option;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.SDFWriter;
import org.openscience.cdk.io.iterator.IteratingMDLReader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import ambit2.tautomers.TautomerManager;

/**
 * The class that does the work.
 * @author nina
 *
 */
public class AppdomainWizard {
	private final static Logger LOGGER = Logger.getLogger(AppdomainWizard.class.getName());
	protected File file;
	protected File resultFile;
	protected boolean all = true;
	
	public void setAll(boolean all) {
		this.all = all;
	}
	public File getResultFile() {
		return resultFile;
	}
	public void setResultFile(File resultFile) {
		this.resultFile = resultFile;
	}

	
	public AppdomainWizard() {
		LOGGER.setLevel(Level.FINEST);
	}
	/**
	 * 
	 * @return
	 */
	public File getFile() {
		return file;
	}
	/**
	 * 
	 * @param file
	 */
	public void setFile(File file) {
		this.file = file;
	}
	/**
	 * 
	 * @param option
	 * @param argument
	 * @throws Exception
	 */
	public void setOption(_option option, String argument) throws Exception {
		if (argument!=null) argument = argument.trim();
		switch (option) {
		case file: {
			if ((argument==null) || "".equals(argument.trim())) return;
			setFile(new File(argument));
			break;
		}
		case output: {
			if ((argument==null) || "".equals(argument.trim())) return;
			setResultFile(new File(argument));
			break;			
		}
		case tautomers: {
			setAll(true);
			if ((argument!=null) && "best".equals(argument.trim().toLowerCase())) setAll(false);
			break;			
		}
		default: 
		}
	}

	/**
	 * 
	 * @return
	 * @throws Exception
	 */
	public int process() throws Exception {
		if (file==null) throw new Exception("File not assigned! Use -f command line option.");
		if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
		int records_read = 0;
		int records_processed = 0;
		int records_error = 0;
		
		InputStream in = new FileInputStream(file);
		/**
		 * cdk-io module
		 * http://ambit.uni-plovdiv.bg:8083/nexus/index.html#nexus-search;classname~IteratingMDLReader
		 */
		IteratingMDLReader reader = null;
		
		SDFWriter writer = new SDFWriter(new OutputStreamWriter(resultFile==null?System.out:new FileOutputStream(resultFile)));
		
		try {
			/**
			 * cdk-slient module
			 * http://ambit.uni-plovdiv.bg:8083/nexus/index.html#nexus-search;classname~SilentChemObjectBuilder
			 */			
			reader = new IteratingMDLReader(in, SilentChemObjectBuilder.getInstance());
			LOGGER.log(Level.INFO, String.format("Reading %s",file.getAbsoluteFile()));
			LOGGER.log(Level.INFO, String.format("Writing %s tautomer(s)",all?"all":"best"));
			while (reader.hasNext()) {
				/**
				 * Note recent versions allow 
				 * IAtomContainer molecule  = reader.next();
				 */
				IAtomContainer molecule  = reader.next();
	
				records_read++;
				try {
					/**
					 * cdk-standard module
					 */
					AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);
					/**
					 * ambit2-model
					 * http://ambit.uni-plovdiv.bg:8083/nexus/index.html#nexus-search;quick~ambit2-model
					 */
					Vector<IAtomContainer> resultTautomers=null;
				
					tautomerManager.setStructure(molecule);
					resultTautomers = tautomerManager.generateTautomersIncrementaly();
					/**
					 * Write the original structure
					 */
					molecule.setProperty("MOLECULE_NO", records_read);
					writer.write(molecule);
					
					/**
					 * Write results
					 */
					IAtomContainer best = null;
					double bestRank = 0;
					for (IAtomContainer tautomer: resultTautomers) {
						try {
							Object rank_property = tautomer.getProperty("TAUTOMER_RANK");
							if (rank_property == null) 
								LOGGER.log(Level.INFO, String.format("No tautomer rank, probably this is the original structure"));
							else {
								double rank = Double.parseDouble(rank_property.toString());
								/**
								 * The rank is energy based, lower rank is better
								 */
								if ((best==null) || (bestRank>rank)) {
									bestRank = rank;
									best = tautomer;
								}
							}
						} catch (Exception x) {
							LOGGER.log(Level.WARNING, x.getMessage());
						}
						tautomer.setProperty("TAUTOMER_OF_MOLECULE_NO", records_read);
						if (all) writer.write(tautomer);
					}
					
					if (!all && (best!=null)) writer.write(best);
					
					records_processed++;;
				} catch (Exception x) {
					System.err.println("*");
					records_error++;
					LOGGER.log(Level.SEVERE, String.format("[Record %d] Error %s\n", records_read, file.getAbsoluteFile()), x);
				}

			}
		} catch (Exception x) {
			x.printStackTrace();
			LOGGER.log(Level.SEVERE, String.format("[Record %d] Error %s\n", records_read, file.getAbsoluteFile()), x);
		} finally {
			try { reader.close(); } catch (Exception x) {}
			try { writer.close(); } catch (Exception x) {}
		}
		LOGGER.log(Level.INFO, String.format("[Records read/processed/error %d/%d/%d] %s", 
						records_read,records_processed,records_error,file.getAbsoluteFile()));
		return records_read;
	}

}
