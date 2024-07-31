# Jace

Just another code editor...<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;... but its simple, lightweight, and runs tasks.

### About

There are a lot of tools to write code in various languages, and Jace here is my suggestion. The main idea is to provide a reliable version of commonly useful features at a fraction of the complexity. Jump straight into coding and running things, with a minimal learning curve.

Can you get more advanced features in something like InteliJ and VSCode? For sure! But these may come at the cost of performance issues. One could also use something lightweight like Notepad++ and run things from the terminal. But there is something to be gained by automating tasks and keeping open multiple runs.

### The editor

As far as editors go, Jace is kept simple to the degree that menus are avoided. Most UI elements are also used via common shortcuts; hover over UI elements for a refresher on shortcuts. The first UI option opens a new project in place of the current one, but there is no issue with multiple Jace instances to work on multiple projects.

**Project navigation:** The important thing to remember is that you can open files by double-clicking on them. Options for the management of files/directories and different types of multiple tab closing appear with right clicks. 

### Run configurations

Jace lets users define run configurations associated with certain file types.
Configurations are read from each project's directory from a ``.jace.yaml` file;
this can be either written and edited by hand, or generated with the UI. 
The file contains named task entries that comprise the three declarations demonstrated below: a) a list of file extensions to associate, b) the language's highlighter (lesser known languages can borrow highlighters from established ones), and c) a command line command to run.

```yaml
// file: .jace.yaml
tasks:
  python:
    extensions: [py]
    highlighter: python
    command: python {path}{file}{ext}
  compile:
    extensions: [cpp, h]
    highlighter: cpp
    command: g++ -I./include src/*.cpp main.cpp -o main -O2 -fdiagnostics-color

```

### Acknowledgements

I want to give a big shout out to the RSyntaxTextArea project. This is an incredibly mature and easy-to-use solution for code highlighting in Java, which is undoubtedly one of Jace's nicest features.

### Contributing

Feel free to point out bugs or make feature requests in this repository's issue page.

