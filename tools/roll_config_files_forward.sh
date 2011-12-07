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

function restore {
    mv $1.new $1
}

echo "Moving from $cversion to $nversion-SNAPSHOT"
simple_replace tools/version
simple_replace tools/version.bat
simple_replace webstart/src/main/jnlp/template.vm

tag_replace app/src/main/bundles/list.xml
ux_tag_replace app/pom.xml

git add tools/version                      \
        tools/version.bat                  \
        webstart/src/main/jnlp/template.vm \
        app/src/main/bundles/list.xml      \
        app/pom.xml

git commit -m "advancing version number in select config files"
