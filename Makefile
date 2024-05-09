# Makefile

# Check if Java 18 or higher is available
MIN_JAVA_VERSION := 18
JAVA_VERSION := $(shell java -version 2>&1 | grep -E -o 'version "([0-9]+)\.([0-9]+)\.([0-9]+)' | awk -F'"' '{print $$2}' | awk -F'.' '{print $$1}')

$(info Detected Java version: $(JAVA_VERSION))

ifeq ($(shell [ $(JAVA_VERSION) -ge $(MIN_JAVA_VERSION) ] && echo 1),1)
    JAVA_HOME := $(shell dirname $(shell dirname $(shell readlink -f $$(which javac))))
    $(info Java version is $(JAVA_VERSION), setting JAVA_HOME to $(JAVA_HOME))
else
    $(error Java $(MIN_JAVA_VERSION) or higher is not installed. Please install Java $(MIN_JAVA_VERSION) or later.)
endif
# Set JAVA_HOME environment variable
export JAVA_HOME

# Java compiler
JC = javac

# Compiler flags
JFLAGS = -d bin -sourcepath src

# Source files directory
SRC_DIR = src

# Output directory
BIN_DIR = bin

# Directory for distribution (JAR)
DIST_DIR = dist

# Main class (replace 'Main' with the name of your main class)
MAIN_CLASS = ModelCountingRanking

# Source files
SOURCES = $(wildcard $(SRC_DIR)/*.java)

# Class files
CLASSES = $(SOURCES:$(SRC_DIR)/%.java=$(BIN_DIR)/%.class)

# JAR file name
JAR_FILE = $(DIST_DIR)/EstiMate.jar

all:
	$(MAKE) compile
	$(MAKE) dist

# Target to run 'ant compile'
compile:
	$(info Compiling with Ant...)
	ant compile

clean:
	ant clean

dist: $(JAR_FILE)

$(JAR_FILE): $(CLASSES)
	@mkdir -p $(DIST_DIR)
	jar cfe $@ $(MAIN_CLASS) -C $(BIN_DIR) .

$(BIN_DIR)/%.class: $(SRC_DIR)/%.java
	@mkdir -p $(BIN_DIR)
	$(JC) $(JFLAGS) $<




