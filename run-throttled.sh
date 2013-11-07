#!/bin/bash
java -Xmx3G -Dthrottle.stream=true -Djava.library.path=ext/  -jar target/active-0.1.0-SNAPSHOT-standalone.jar
