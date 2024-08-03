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
Configurations are read from each project's directory from a ``.mango.yaml`.
For easy of use, Mango offers an organized dialog to edit this file (try to 
open the file, if it's there, or click on the :gear: button). 
The configuration contains several named tasks, each with its own list of
associated file extensions to associate, the highlighter 
(lesser known languages can borrow highlighters from established ones),
and a command to run. Multiple commands for the same file appear as options
to choose from. For instance, in the screenshot above, the running the project's
.gitignore corresponds is configured to provide a choice between pushing and pulling.

When writting a command, you may use bracketed substrings to customize it
for the currently open file: {path}{file}{ext} corresponds to the full path of your file.
For example, the {file} part when editing "c:\users\maniospas\test.py" is replaced by "test"
without the quotations). 
Other bracketed strings are replaced by a message the the user is asked to provide. For
example, in the screenshot above, the push task includes a custom message that the
user provides.

:checkmark: **Planned:** Common defaults for working with different types of projects will
become available in the future.

### Acknowledgements

I want to give a big shout out to the RSyntaxTextArea project, which Mango uses as a robust syntax highlighter.

### Contributing

Feel free to point out bugs or make feature requests in this repository's issue page.

