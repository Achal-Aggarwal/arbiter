# Arbiter [![Build Status](https://travis-ci.org/Achal-Aggarwal/arbiter.svg)](https://travis-ci.org/Achal-Aggarwal/arbiter)
Arbiter is a utility for generating XML Oozie workflows from a YAML specification.

## Features
1. Configuration driven: The mapping of the YAML workflow definition to the generated XML is highly configurable.
2. Automatic dependency resolution and parallelism: Arbiter workflows are specified in terms of the dependencies between actions, rather than requiring the author to manually specify the flow.  Arbiter will order the workflow actions to satisfy the dependencies as well as insert fork/join pairs to run actions in parallel when possible.

## Building
Arbiter requires at least Java 7.

Arbiter is built with Maven.  Run `mvn clean package` to build an uber-JAR suitable for use in running Arbiter.

I. Add sonatype repository
```xml
<repository>
  <id>sonatype</id>
  <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
</repository>
```
II. Add dependency
```xml
<dependency>
  <groupId>net.achalaggarwal.arbiter</groupId>
  <artifactId>arbiter</artifactId>
  <version>0.1-SNAPSHOT</version>
</dependency>
```

## Usage
Before writing workflows with Arbiter, you must define at least one configuration file.  See [Configuration](https://github.com/Achal-Aggarwal/arbiter/wiki/Configuration) for details on writing a configuration file.

See [Workflow Definition](https://github.com/Achal-Aggarwal/arbiter/wiki/Workflow-Definition) for details on writing workflows with Arbiter.

### Command Line Options

Flag        | Meaning
----------- | -------
-c <path>   | Specifies the path to a configuration file.  Required once, but can be specified multiple times to provide multiple configuration files.
-l <path>   | Specifies the path to a low-priority configuration file.  Low priority configurations will be overridden by standard configurations if they define overlapping settings.  Optional.
-i <path>   | Specifies the path to a YAML workflow definition to process.  Required once, but can be specified multiple times to process multiple workflows.
-o <path>   | Specifies the directory to which Arbiter will output the generated workflows.  Required.
-g [<format>]| Enables generating a image of the workflow graph using Graphviz.  The `dot` tool must be installed and on the `PATH` for this to work.  SVG is the default format but any format supported by `dot` may be specified as an argument to for this flag.
-h          | Prints a usage message         

### Running Arbiter
First build an Arbiter uber-JAR as described in the Building section above.  Arbiter can then be invoked like so:

```
java -jar arbiter.jar [OPTIONS]
```