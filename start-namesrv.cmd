@echo off
title RocketMQ-NameServer
set RKT=D:\rocketmq-all-5.1.4-bin-release\rocketmq-all-5.1.4-bin-release
set CP=.;%RKT%\conf;%RKT%\lib\*
java -server -Xms512m -Xmx512m -Xmn256m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=320m -XX:+UseG1GC -XX:-OmitStackTraceInFastThrow -XX:-UseLargePages --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED --add-exports=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED --add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED --add-exports=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED -cp "%CP%" org.apache.rocketmq.namesrv.NamesrvStartup
