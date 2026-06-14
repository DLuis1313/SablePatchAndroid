#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0

##############################################################################
# Gradle start up script for UN*X
##############################################################################

# Attempt to set APP_HOME
APP_HOME=$(cd "$(dirname "$0")" && pwd)

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVA_HOME/bin/java" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
