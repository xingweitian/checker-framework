.PHONY: all

all:
	rm -f Out.txt Test.class LiveVariable.class
	javac -cp ${CHECKERFRAMEWORK}/checker/dist/checker.jar LiveVariable.java
	java -Xbootclasspath/p:${CHECKERFRAMEWORK}/checker/dist/javac.jar \
      -cp ${CHECKERFRAMEWORK}/checker/dist/checker.jar:. LiveVariable > Out.txt 2>&1 || true
	diff -u Expected.txt Out.txt
