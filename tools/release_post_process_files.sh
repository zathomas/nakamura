#/bin/sh
set -o nounset
set -o errexit
cversion=$1
nversion=$2

function simple_replace {
    sed "s/$cversion/$nversion-SNAPSHOT/" $1 > $1.new
    restore $1
}

function tag_replace {
    sed "s/\>$cversion\</\>$nversion-SNAPSHOT\</" $1 > $1.new
    restore $1
}

function ux_tag_replace {
    sed "s/\<ux\>$cversion\<\/ux\>/\<ux\>$nversion-SNAPSHOT\<\/ux\>/" $1 > $1.new
    restore $1
}

function snapshot_to_snapshot {
    sed "s/\>$cversion-SNAPSHOT\</\>$nversion-SNAPSHOT\</" $1 > $1.new
    restore $1
}

function restore {
    mv $1.new $1
    git add $1
}

echo "Moving from $cversion to $nversion-SNAPSHOT"
simple_replace tools/version
simple_replace tools/version.bat
simple_replace webstart/src/main/jnlp/template.vm

otherpoms=`find . -name "pom.xml" -path "./sandbox/*" -or -path "./contrib/*" -name "pom.xml"`
for file in $otherpoms
  do
    snapshot_to_snapshot $file
  done
  
tag_replace app/src/main/bundles/list.xml
tag_replace webstart/pom.xml
tag_replace modelling/pom.xml

ux_tag_replace app/pom.xml

git commit -m "switching from version to next SNAPSHOT in config files"
