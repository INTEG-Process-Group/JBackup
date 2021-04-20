# JNIOR
Made for the Series 4 JNIOR.

The JNIOR is an easy-to-use, flexible automation controller with digital and analog I/O, Ethernet and serial connectivity, and add-on software applications for monitoring and controlling equipment or a process for a variety of applications.

Please go to our website, https://jnior.com, for more information.

# JBakup
Program to archive BAK file data. This runs at 15 minute intervals. It identifies new BAK files and accumulates them in a ZIP library located in /flash/baks.

## Running JBakup
JBakup can be set to run on bootup.  To do this you need to load the application in the /flash/ directory on the JNIOR and create a run key in the registry.

`jr /> reg run/jbakup = jbakup`

## Editing the Application
You can download the repository and make any changes you like to the code.  The code is written in Java using the NetBeans IDE.  Any Java IDE should work as long as you can output a `.JAR file` or Java Archive.  The PC will need to have a Library defined for the Janos Runtime library.  In NetBeans the Libraries are defined under `Tools > Libraries`.
