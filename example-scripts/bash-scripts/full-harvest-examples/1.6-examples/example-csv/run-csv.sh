#!/bin/bash

#Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
#All rights reserved.
#This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html

# set to the directory where the harvester was installed or unpacked
# HARVESTER_INSTALL_DIR is set to the location of the installed harvester
#	If the deb file was used to install the harvester then the
#	directory should be set to /usr/share/vivo/harvester which is the
#	current location associated with the deb installation.
#	Since it is also possible the harvester was installed by
#	uncompressing the tar.gz the setting is available to be changed
#	and should agree with the installation location
HARVESTER_INSTALL_DIR=/usr/local/src/VIVO-Harvester
export HARVEST_NAME=example-csv
export DATE=`date +%Y-%m-%d'T'%T`

# Add harvester binaries to path for execution
# The tools within this script refer to binaries supplied within the harvester
#	Since they can be located in another directory their path should be
#	included within the classpath and the path environment variables.
export PATH=$PATH:$HARVESTER_INSTALL_DIR/bin
export CLASSPATH=$CLASSPATH:$HARVESTER_INSTALL_DIR/bin/harvester.jar:$HARVESTER_INSTALL_DIR/bin/dependency/*
export CLASSPATH=$CLASSPATH:$HARVESTER_INSTALL_DIR/build/harvester.jar:$HARVESTER_INSTALL_DIR/build/dependency/*

# Exit on first error
# The -e flag prevents the script from continuing even though a tool fails.
#	Continuing after a tool failure is undesirable since the harvested
#	data could be rendered corrupted and incompatible.
set -e

# Supply the location of the detailed log file which is generated during the script.
#	If there is an issue with a harvest, this file proves invaluable in finding
#	a solution to the problem. It has become common practice in addressing a problem
#	to request this file. The passwords and usernames are filtered out of this file
#	to prevent these logs from containing sensitive information.
echo "Full Logging in $HARVEST_NAME.$DATE.log"
if [ ! -d logs ]; then
  mkdir logs
fi
cd logs
touch $HARVEST_NAME.$DATE.log
ln -sf $HARVEST_NAME.$DATE.log $HARVEST_NAME.latest.log
cd ..

#clear old data
# For a fresh harvest, the removal of the previous information maintains data integrity.
#	If you are continuing a partial run or wish to use the old and already retrieved
#	data, you will want to comment out this line since it could prevent you from having
# 	the required harvest data.  
rm -rf data


# Import CSV
# Takes the data from a comma-separated-values file and places it in a relational database.  Then
#   JDBCFetch, in the next step, can use this to generate XML.
echo Import CSV
harvester-csvtojdbc -X csvtojdbc.config.xml

# Execute Fetch
# This stage of the script is where the information is gathered together into one local
#	place to facilitate the further steps of the harvest. The data is stored locally
#	in a format based off of the source. The format is a form of RDF but not in the VIVO ontology
# The JDBCFetch tool in particular takes the data from the chosen source described in its
#	configuration XML file and places it into record set in the flat RDF directly 
#	related to the rows, columns and tables described in the target database.
echo Execute jdbcfetch
harvester-jdbcfetch -X jdbcfetch.config.xml

# Execute Translate
# This is the part of the script where the input data is transformed into valid RDF
#   Translate will apply an xslt file to the fetched data which will result in the data 
#   becoming valid RDF in the VIVO ontology
echo Execute translate
harvester-xsltranslator -X xsltranslator.config.xml

# Execute Transfer to import from record handler into local temp model
# From this stage on the script places the data into a Jena model. A model is a
#	data storage structure similar to a database, but in RDF.
# The harvester tool Transfer is used to move/add/remove/dump data in models.
# For this call on the transfer tool:
# -s refers to the source translated records file, which was just produced by the translator step
# -o refers to the destination model for harvested data
# -d means that this call will also produce a text dump file in the specified location 
echo Execute initial transfer to triple store
harvester-transfer -s translated-records.config.xml -o harvested-data.model.xml -d data/harvested-data/imported-records.rdf.xml

# Execute Score
# In the scoring phase the data in the harvest is compared to the data within Vivo and a new model
# 	is created with the values / scores of the data comparsions. 

# Execute Score for People
echo  Execute Score for People
harvester-score -X score-people.config.xml

# Execute Score for Departments
echo Execute Score for Departments
harvester-score -X score-departments.config.xml

# Find matches using scores and rename nodes to matching uri
# Using the data model created by the score phase, the match process changes the harvested uris for
# 	comparsion values above the chosen threshold within the xml configuration file.
# Execute Match for People and Departments
echo  Execute Match for People and Departments
harvester-match -X match-people-departments.config.xml

# Truncate Score Data model
# Since we are finished with the scoring data for people and departments,
#   we need to clear out all that old data before we add more
echo Truncate Score Data model
harvester-jenaconnect -j score-data.model.xml -t

# Execute Score for Positions
echo  Execute Score for Positions
harvester-score -X score-positions.config.xml

# Execute Match for Positions
echo  Execute Match for Positions
harvester-match -X match-positions.config.xml

#Truncate Score Data model
# Since we are finished with the scoring data for people and departments,
#   we need to clear out all that old data before we add more
echo Truncate Score Data model
harvester-jenaconnect -j score-data.model.xml -t

# Execute ChangeNamespace to get unmatched  into current namespace
# This is where the new people, departments, and positions from the harvest are given uris within the namespace of Vivo
# 	If there is an issue with uris being in another namespace, this is the phase
#	which should give some light to the problem.
# Execute ChangeNamespace for People
echo Execute ChangeNamespace for People
harvester-changenamespace -X changenamespace-people.config.xml

# Execute ChangeNamespace for Departments
echo Execute ChangeNamespace for Departments
harvester-changenamespace -X changenamespace-departments.config.xml

# Execute ChangeNamespace for Positions
echo Execute ChangeNamespace for Positions
harvester-changenamespace -X changenamespace-positions.config.xml

# Execute ChangeNamespace for vcards
echo  Execute ChangeNamespace for vcards
harvester-changenamespace -X changenamespace-vcard.config.xml

# Execute ChangeNamespace for timeIntervals
echo  Execute ChangeNamespace for timeInterval
harvester-changenamespace -X changenamespace-timeinterval.config.xml

# Perform an update
# The harvester maintains copies of previous harvests in order to perform the same harvest twice
#   but only add the new statements, while removing the old statements that are no longer
#   contained in the input data. This is done in several steps of finding the old statements,
#   then the new statements, and then applying them to the Vivo main model.

# Find Subtractions
# When making the previous harvest model agree with the current harvest, the statements that exist in
#	the previous harvest but not in the current harvest need to be identified for removal.
echo Find Subtractions
harvester-diff -X diff-subtractions.config.xml

# Find Additions
# When making the previous harvest model agree with the current harvest, the statements that exist in
#	the current harvest but not in the previous harvest need to be identified for addition.
echo Find Additions
harvester-diff -X diff-additions.config.xml

# Apply Subtractions to Previous model
echo Apply Subtractions to Previous model
harvester-transfer -o previous-harvest.model.xml -r data/vivo-subtractions.rdf.xml -m
# Apply Additions to Previous model
echo  Apply Additions to Previous model
harvester-transfer -o previous-harvest.model.xml -r data/vivo-additions.rdf.xml

# Now that the changes have been applied to the previous harvest and the harvested data in vivo
#	agree with the previous harvest, the changes are now applied to the vivo model.
# Apply Subtractions to VIVO 
echo Apply Subtractions to VIVO
harvester-transfer -o vivo.model.xml -r data/vivo-subtractions.rdf.xml -m
# Apply Additions to VIVO for pre-1.2 versions
echo Apply Additions to VIVO
harvester-transfer -o vivo.model.xml -r data/vivo-additions.rdf.xml

#Output some counts
ORGS=`cat data/vivo-additions.rdf.xml | grep 'http://xmlns.com/foaf/0.1/Organization' | wc -l`
PEOPLE=`cat data/vivo-additions.rdf.xml | grep 'http://xmlns.com/foaf/0.1/Person' | wc -l`
POSITIONS=`cat data/vivo-additions.rdf.xml | grep 'positionForPerson' | wc -l`
echo "Imported $ORGS organizations, $PEOPLE people, and $POSITIONS positions"

echo 'Harvest completed successfully'
