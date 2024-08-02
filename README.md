# Mango

A tasty code editor; simple, lightweight, and runnable.

### About

There are a lot of tools for code writing, and Mango here 
is what I would like the experience to feel like:
common useful features at a fraction of the 
complexity. As simple as eating a fruit! 
:mango: Even first-time users can jump straight into coding 
and running things.

Mango is kept simple by providing a small set of vital operations 
that do not need menus organize. Most UI buttons are mapped to well-known 
shortcuts; hover over them for a refresher.

![preview](preview.png)


### Run configurations

Tasks (e.g., compilation) can be associated with certain file types.
Configurations are read from each project's directory from a ``.mango.yaml` file
that the editor edits through a dialog. 
The file contains named task entries, each comprising three types of info:
a) a list of file extensions to associate, b) the language's highlighter 
(lesser known languages can borrow highlighters from established ones),
and c) a command line command to run.

```yaml
// file: .mango.yaml
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

I want to give a big shout out to the RSyntaxTextArea project, which Mango uses as a robust syntax highlighter.

### Contributing

Feel free to point out bugs or make feature requests in this repository's issue page.

