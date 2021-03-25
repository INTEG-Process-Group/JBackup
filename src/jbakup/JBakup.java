/**
 * Program to archive BAK file data. This runs at 15 minute intervals. It
 *  identifies new BAK files and accumulates them in a ZIP library located
 *  in /flash/baks.
 */
package jbakup;
import com.integpg.system.JANOS;
import com.integpg.system.Watchdog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
/**
 * @author Bruce Cloutier
 */
public class JBakup {
    public static void main(String[] args) throws Throwable {
        
        // Requires JANOS v2.0 Build 3.3
        if (Long.decode(JANOS.getRegistryString("$BuildTag", "0")) < 0x200300030003L) {
            JANOS.syslog("[JBakup] OS version error. Exiting.");
            System.exit(1);
        }
        
        // Run only a single instance of this program
        if (JANOS.registerProcess("a69bf9e3-a2ff-4d") > 1) {
            JANOS.syslog("[JBakup] Cannot run more than one instance. Exiting.");
            System.exit(1);
        }
        
        // Create destination folder if new
        new File("/flash/baks").mkdir();
        // Program is restarted via watchdog should any exception be thrown. See error.log
        //  for details. Watchdog should be refreshed every 15 minutes and so we expire
        //  after 20.
        Watchdog watchdog = new Watchdog("BAK File Archiver");
        watchdog.setAction(Watchdog.WDT_RESTART);
        watchdog.activate(20*60*1000);
        
        // Background routine scans BAK file status 15 minutes after boot and every
        //  15 minutes thereafter.
        for (;;) {
            // run on the quarter hour
            long MINUTE = 15*60*1000;
            long wait = MINUTE - ((System.currentTimeMillis() + MINUTE) % MINUTE);
            Thread.sleep(wait);
            watchdog.refresh();
            
            // scan BAK files
            do_scan();
        }
    }
    
    public static void do_scan() throws Throwable {
        // obtain list of BAK files in the root
        File root = new File("/*.bak");
        String[] files = root.list();
        
        // stop if nothing to do
        if (files.length == 0)
            return;
        // process each BAK file
        for (String file : files) {
            
            // determine name of the target compressed file
            String target = file.substring(0, file.length() - 4) + ".zip";
            
            // Create the update task. This varries depending on whether or not the
            //  target already exists.
            File src = new File("/" + file);
            File lib = new File("/flash/baks/" + target);
            
            // Check modification dates. We process only newly generated BAK files.
            if (lib.lastModified() >= src.lastModified())
                continue;
            
            // ZIP target exists. We move it to the /temp folder and extract the BAK
            //  file. We append the new log data. Finally the ZIP file is
            //  updated and returned to /flash. We clean up.
            if (lib.isFile()) {
                PrintWriter task = new PrintWriter(new FileOutputStream("/bkup_task.bat"));
                task.println("arc -eo /flash/baks/" + target + " -p /temp " + file);
                
                // script to append to BAK and limit the overall size while insuring that
                //  the file begins at the beginnning of a line. Maximium size is 2.5MB.
                task.println("<?\n" +
                    "    $max = 2621440;\n" +
                    "    $src = \"/" + file + "\";\n" +
                    "    $dest = \"/temp/" + file + "\";\n" +
                    "\n" +
                    "    $content = fread($dest);\n" +
                    "    $newdata = fread($src);");
                
                // jniorboot.log operates as a queue. The BAK file updates with every boot. So we 
                //  only archive new content. Note this works because lines are timestamped uniquely.
                if (file.equals("jniorboot.log.bak"))
                    task.println("\n" +
                        "    $pos = strrpos($content, \"\\n\", -2);\n" +
                        "    $lastln = substr($content, ++$pos);\n" +
                        "    $pos = strpos($newdata, $lastln);\n" +
                        "    if ($pos > 0) {\n" +
                        "        $pos = strpos($newdata, \"\\n\", $pos);\n" +
                        "        $newdata = substr($newdata, ++$pos);\n" +
                        "    }");
                
                task.println("    $content .= $newdata;\n" +
                    "\n" +
                    "    if (strlen($content) > $max) {\n" +
                    "        $pos = strpos($content, \"\\n\", -$max);\n" +
                    "        $content = substr($content, ++$pos);\n" +
                    "    }\n" +
                    "\n" +
                    "    fwrite($dest, $content);\n" +
                    "?>");
                
                task.println("arc -m /flash/baks/" + target + " -p / /temp/" + file);
                task.println("rm bkup_task.bat");
                task.close();
            }
            
            // ZIP target will be new. We crete the ZIp file and move it to Flash.
            //  Then we clean up.
            else {
                PrintWriter task = new PrintWriter(new FileOutputStream("/bkup_task.bat"));
                task.println("arc -a /flash/baks/" + target + " /" + file);
                task.println("rm bkup_task.bat");
                task.close();
            }
            
            // Execute this task and time it
            long start = JANOS.uptimeMillis();
            Process proc = Runtime.getRuntime().exec("/bkup_task.bat");
            proc.waitFor();
            double duration = (double)(JANOS.uptimeMillis() - start)/1000.;
            
            // log the event
            JANOS.syslog(String.format("[JBakup] Archived /%s (%,d bytes %.2f secs)", 
                    file, (int)lib.length(), duration));
        }
    }
    
}