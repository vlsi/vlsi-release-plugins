#!/bin/sh

# Make file name predictable
GIT_URL=$1
VERSION=1.0.0

# Build CDP jar
./gradlew :plugins:checksum-dependency-plugin:jar -Pproject.version=$VERSION

git clone --depth 100 $GIT_URL e2e

cd e2e

CDP_JAR=../plugins/checksum-dependency-plugin/build/jars/checksum-dependency-plugin-$VERSION.jar

SETTINGS=settings.gradle

if [ -f $SETTINGS.kts ] ; then
  SETTINGS=$SETTINGS.kts
fi

# Add new CDP to the top of the settings
echo "buildscript { dependencies { classpath(\"$CDP_JAR\") } }" > $SETTINGS.2

# Remove SHA check from the plugin itself
grep -v Buildscript $SETTINGS >> $SETTINGS.2

mv $SETTINGS.2 $SETTINGS.1
