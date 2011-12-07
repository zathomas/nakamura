#/bin/sh
set -o nounset
set -o errexit
cversion=$1

function ux_tag_replace {
    sed "s/\<ux\>$cversion-SNAPSHOT\<\/ux\>/\<ux\>$cversion\<\/ux\>/" $1 > $1.new
    restore $1
}

function restore {
    mv $1.new $1
}

echo "Moving ux property from $cversion-SNAPSHOT to $cversion"
ux_tag_replace app/pom.xml

git add app/pom.xml

git commit -m "advancing version number for ux property"

