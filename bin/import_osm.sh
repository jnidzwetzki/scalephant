#!/bin/bash
#*******************************************************************************
#
#    Copyright (C) 2015-2018 the BBoxDB project
#  
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#  
#      http://www.apache.org/licenses/LICENSE-2.0
#  
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License. 
#    
#*******************************************************************************
#
# Import data for the demo 
#
#########################################

# Is the environment configured?
if [ -z "$BBOXDB_HOME" ]; then
   echo "Your environment variable \$(BBOXDB_HOME) is empty. Please check your installation."
   exit -1
fi

# Load all required functions and variables
source $BBOXDB_HOME/bin/bootstrap.sh

make_script_on_error_fail

function wait_if_needed() {
	if [[ $1 != "nowait" ]]; then
	   read -p "Press enter to continue"
	fi
}

if [ "$#" -lt 1 ]; then
   echo "$0 <basedir> [nowait]"
   exit -1
fi

base=$1
wood="$base/WOOD"
road="$base/ROAD"

if [ ! -f ${wood} ]; then
   echo "${wood} - file not found"
   exit -1
fi

if [ ! -f ${road} ]; then
   echo "${road} - file not found"
   exit -1
fi

if [ ! -f ${wood}_FIXED ]; then
    echo "Extracting geometries from ${wood}, this may take while..."
    egrep -v '"type":"Point"' ${wood} > ${wood}_FIXED
fi

if [ ! -f ${road}_FIXED ]; then
    echo "Extracting geometries from ${road}, this may take while..."
    egrep -v '"type":"Point"' ${road} > ${road}_FIXED
fi

road_size=$(stat -c%s "${road}_FIXED")

one_gb="1073741824"
one_gb_in_mb=$(($one_gb / 1048576))

gigabytes=$(($road_size / $one_gb))
partitions=$(($gigabytes * 4))
groupname="osmgroup"

# Create at least instances * 8 partitions
instances=$($BBOXDB_HOME/bin/cli.sh -action show_instances | grep READY | wc -l)
min_partitions=$(($instances * 8))

partitions=$(( $partitions < $min_partitions ? $min_partitions : $partitions ))

echo "Discovered $instances instances, creating $partitions partitions"

$BBOXDB_HOME/bin/cli.sh -action delete_dgroup -dgroup $groupname
$BBOXDB_HOME/bin/cli.sh -action create_dgroup -dgroup $groupname -replicationfactor 1 -dimensions 2 -maxregionsize $one_gb_in_mb 

echo "===== Starting prepartitioning ($partitions partitions) ====="
wait_if_needed $2
$BBOXDB_HOME/bin/cli.sh -action prepartition -file $road -format geojson -dgroup $groupname -partitions $partitions

echo "===== Creating tables ====="
wait_if_needed $2
$BBOXDB_HOME/bin/cli.sh -action create_table -table ${groupname}_roads
$BBOXDB_HOME/bin/cli.sh -action create_table -table ${groupname}_forests
$BBOXDB_HOME/bin/cli.sh -action import -file ${road}_FIXED -format geojson -table ${groupname}_roads
$BBOXDB_HOME/bin/cli.sh -action import -file ${wood}_FIXED -format geojson -table ${groupname}_forests

exit_script_successfully

