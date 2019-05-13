#!/usr/bin/env bash


./apache-activemq-5.15.6/bin/activemq start

mvn exec:java -Dexec.mainClass="org.mccaughey.SendPoint"
