.PHONY: all build run

all: build

build:
	javac -cp "lib/*:src" -d bin src/rts/MicroRTS.java
	javac -cp "lib/*:src" -d bin $(shell find src -name "*.java")
	cd bin && find ../lib -name "*.jar" | xargs -n 1 jar xvf
	jar cvf microrts.jar -C bin .

run:
	java -cp "lib/*:bin" rts.MicroRTS