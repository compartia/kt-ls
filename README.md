# KT Advance Language server  

Requires that you have Java 8 installed on your system.

## Installation

TODO:

## [Issues](https://github.com/compartia/kt-ls/issues)

## Features

   
## Usage

VSCode will provide autocomplete and help text using:
* .java files anywhere in your workspace
* Java platform classes
* External dependencies specified using `pom.xml`, Bazel, or [settings](#Settings)

## Settings
   
### Optional settings

Optional user settings. These should be set in global settings `Preferences -> Settings`, not in your project directory.

* `java.home` Installation directory of Java 8

 
## Directory structure
 
## Typescript Visual Studio Code extension

"Glue code" that launches the external java process
and connects to it using [vscode-languageclient](https://www.npmjs.com/package/vscode-languageclient).

    package.json (node package file)
    tsconfig.json (typescript compilation configuration file)
    tsd.json (project file for tsd, a type definitions manager)
    lib/ (typescript sources)
    out/ (compiled javascript)

## Design

This extension consists of an external java process, 
which communicates with vscode using the [language server protocol](https://github.com/Microsoft/vscode-languageserver-protocol). 

  
## Logs

The java service process will output a log file to kt-ls.log;

## Contributing

If you have npm and maven installed,
you should be able to install locally using 

    npm install -g vsce
    npm install
    ./scripts/install.sh
