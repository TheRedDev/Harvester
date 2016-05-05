/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.qualify;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.IterableAdaptor;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.JenaConnect;
import org.vivoweb.harvester.util.repo.MemJenaConnect;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.Lock;

/**
 * Smush
 * @author Cornell University VIVO Team (Algorithm)
 * @author James Pence (jrpence@ufl.edu) (Harvester Tool)
 */
public class Smush {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(Smush.class);
	/**
	 * model containing statements to be scored
	 */
	private JenaConnect inputJC;
	/**
	 * model in which to store temp copy of input and vivo data statements
	 */
	private JenaConnect outputJena;
	/**
	 * the predicates to look for in inputJC model
	 */
	private List<String> inputPredicates;
	/**
	 * limit match Algorithm to only match rdf nodes in inputJC whose URI begin with this namespace
	 */
	private String namespace;
	/**
	 * Change the input model to match the output model
	 */
	private boolean inPlace;
	
	/**
	 * Constructor
	 * @param inputJena model containing statements to be smushed
	 * @param outputJena model containing only resources about the smushed statements is returned
	 * @param inputPredicates the predicates to look for in inputJC model
	 * @param namespace limit match Algorithm to only match rdf nodes in inputJC whose URI begin with this namespace
	 * @param inPlace replace the input model with the output model
	 */
	public Smush(JenaConnect inputJena, JenaConnect outputJena, List<String> inputPredicates, String namespace, boolean inPlace) {
		if(inputJena == null) {
			throw new IllegalArgumentException("Input model cannot be null");
		}
		this.inputJC = inputJena;
		
		this.outputJena = outputJena;
		if(inputPredicates == null) {
			throw new IllegalArgumentException("Input Predicate cannot be null");
		}
		this.inputPredicates = inputPredicates;
		this.namespace = namespace;
		this.inPlace = inPlace;
	}
	
	/**
	 * Constructor
	 * @param inputJena model containing statements to be smushed
	 * @param inputPredicates the predicates to look for in inputJC model
	 * @param namespace limit match Algorithm to only match rdf nodes in inputJC whose URI begin with this namespace
	 */
	public Smush(JenaConnect inputJena, List<String> inputPredicates, String namespace) {
		this(inputJena, null, inputPredicates, namespace, true);
	}
	
	/**
	 * Constructor
	 * @param args argument list
	 * @throws IOException error parsing options
	 * @throws UsageException user requested usage message
	 */
	private Smush(String... args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor Scoring.close();
	 * @param opts parsed argument list
	 * @throws IOException error parsing options
	 */
	private Smush(ArgList opts) throws IOException {
		this(
			JenaConnect.parseConfig(opts.get("i"), opts.getValueMap("I")), 
			JenaConnect.parseConfig(opts.get("o"), opts.getValueMap("O")), 
			opts.getAll("P"), 
			opts.get("n"), 
			opts.has("r")
		);
	}
	
	/**
	 * Get the ArgParser
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("Smush");
		// Models
		parser.addArgument(new ArgDef().setShortOption('i').setLongOpt("inputJena-config").withParameter(true, "CONFIG_FILE").setDescription("inputJC JENA configuration filename").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('I').setLongOpt("inputOverride").withParameterValueMap("JENA_PARAM", "VALUE").setDescription("override the JENA_PARAM of inputJC jena model config using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('o').setLongOpt("outputJena-config").withParameter(true, "CONFIG_FILE").setDescription("inputJC JENA configuration filename").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('O').setLongOpt("outputOverride").withParameterValueMap("JENA_PARAM", "VALUE").setDescription("override the JENA_PARAM of outputJena jena model config using VALUE").setRequired(false));
		
		// Parameters
		parser.addArgument(new ArgDef().setShortOption('P').setLongOpt("inputJena-predicates").withParameters(true, "PREDICATE").setDescription("PREDICATE(s) on which, to match. Multiples are done in series not simultaineously.").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("namespace").withParameter(true, "NAMESPACE").setDescription("only match rdf nodes in inputJC whose URI begin with NAMESPACE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('r').setLongOpt("replace").setDescription("replace input model with changed / output model").setRequired(false));
		return parser;
	}
	
	/**
	 * A simple resource smusher based on a supplied inverse-functional property.
	 * @param inputJC - model to operate on
	 * @param subsJC model to hold subtractions
	 * @param addsJC model to hold additions
	 * @param property - property for smush
	 * @param ns - filter on resources addressed (if null then applied to whole model)
	 */
	public static void findSmushResourceChanges(JenaConnect inputJC, JenaConnect subsJC, JenaConnect addsJC, String property, String ns) {
		log.debug("Smushing on property <" + property + "> within "+((ns != null )?"namespace <"+ ns + ">":"any namespace"));
		Model inModel = inputJC.getJenaModel();
		Model subsModel = subsJC.getJenaModel();
		Model addsModel = addsJC.getJenaModel();
		Property prop = inModel.createProperty(property);
		inModel.enterCriticalSection(Lock.READ);
		try {
			// for each object value of the given property
			for(RDFNode obj : IterableAdaptor.adapt(inModel.listObjectsOfProperty(prop))) {
				boolean first = true;
				Resource smushToThisResource = null;
				// loop through subjects with this property-object pairing 
				for(Resource subj : IterableAdaptor.adapt(inModel.listSubjectsWithProperty(prop, obj))) {
					// only look at subject resources in the requested namespace if one specified
					if(subj.getNameSpace().equals(ns) || ns == null){
						// the first subject resource is found and recorded
						if (first) {
							smushToThisResource = subj;
							first = false;
							log.debug(" Smushing all instances for ["+obj+"] into <"+subj+">");
						// the rest are smushed into the first resource
						} else {
							log.trace("   <"+subj+">");
							// for each statement of the to-be-smushed resource
							for(Statement stmt : IterableAdaptor.adapt(inModel.listStatements(subj,(Property)null,(RDFNode)null))) {
								log.trace("     <"+stmt.getPredicate()+"> <"+stmt.getObject()+">");
								// save the current statement to the 'subtraction' model
								subsModel.add(stmt.getSubject(), stmt.getPredicate(), stmt.getObject());
								// save the statement with new subject resource to the 'addition' model
								addsModel.add(smushToThisResource, stmt.getPredicate(), stmt.getObject());
							}
							// for each statement with to the to-be-smushed resource as the object
							for(Statement stmt : IterableAdaptor.adapt(inModel.listStatements((Resource) null, (Property)null, subj))) {
								log.trace("     <"+stmt.getSubject()+"> <"+stmt.getPredicate()+">");
								// save the current statement to the 'subtraction' model
								subsModel.add(stmt.getSubject(), stmt.getPredicate(), stmt.getObject());
								// save the statement with new object resource to the 'addition' model
								addsModel.add(stmt.getSubject(), stmt.getPredicate(), smushToThisResource);
							}
						}
					}
				}
			}
		} finally {
			inModel.leaveCriticalSection();
		}
	}
	
	/**
	 * Execute is that method where the smushResoures method is ran for each predicate.
	 */
	public void execute() {
		JenaConnect subsJC = new MemJenaConnect();
		JenaConnect addsJC = new MemJenaConnect();
		for(String runName : this.inputPredicates) {
			findSmushResourceChanges(this.inputJC, subsJC, addsJC, runName, this.namespace);
		}
		try {
			log.debug("Subs:\n" + subsJC.exportRdfToString());
		} catch(IOException e) {
			// Do Nothing
		}
		try {
			log.debug("Adds:\n" + addsJC.exportRdfToString());
		} catch(IOException e) {
			// Do Nothing
		}
		if(this.inPlace){
			log.trace("removing old statements");
			this.inputJC.removeRdfFromJC(subsJC);
			log.trace("inserting new statements");
			this.inputJC.loadRdfFromJC(addsJC);
		}
		if(this.outputJena != null) {
			this.outputJena.loadRdfFromJC(this.inputJC);
			this.outputJena.removeRdfFromJC(subsJC);
			this.outputJena.loadRdfFromJC(addsJC);
			this.outputJena.sync();
		}
		this.inputJC.sync();
	}

	/**
	 * Main method
	 * @param args command line arguments
	 */
	public static void main(String... args) {
		Exception error = null;
		try {
			InitLog.initLogger(args, getParser());
			log.info(getParser().getAppName() + ": Start");
			new Smush(args).execute();
		} catch(IllegalArgumentException e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:",e);
			System.out.println(getParser().getUsage());
			error = e;
		} catch(UsageException e) {
			log.info("Printing Usage:");
			System.out.println(getParser().getUsage());
			error = e;
		} catch(Exception e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:",e);
			error = e;
		} finally {
			log.info(getParser().getAppName() + ": End");
			if(error != null) {
				System.exit(1);
			}
		}
	}
}
