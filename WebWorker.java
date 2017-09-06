/**
* Web worker: an object of this class executes in its own new thread
* to receive and respond to a single HTTP request. After the constructor
* the object executes on its "run" method, and leaves when it is done.
*
* One WebWorker object is only responsible for one client connection. 
* This code uses Java threads to parallelize the handling of clients:
* each WebWorker runs in its own thread. This means that you can essentially
* just think about what is happening on one client at a time, ignoring 
* the fact that the entirety of the webserver execution might be handling
* other clients, too. 
*
* This WebWorker class (i.e., an object of this class) is where all the
* client interaction is done. The "run()" method is the beginning -- think
* of it as the "main()" for a client interaction. It does three things in
* a row, invoking three methods in this class: it reads the incoming HTTP
* request; it writes out an HTTP header to begin its response, and then it
* writes out some HTML content for the response content. HTTP requests and
* responses are just lines of text (in a very particular format). 
*
**/
//Modified slightly by Zachary Lowery for Project 1 of CS371.
//Modified to allow serving of actual html files.
//Modified to send a response header as well as a 404 not found header.
//Modified to allow for weak dynamic content generation.
//Date: 9/5/2017


import java.net.Socket;
import java.lang.Runnable;
import java.io.*;
import java.util.Date;
import java.text.DateFormat;
import java.util.TimeZone;

public class WebWorker implements Runnable
{

private Socket socket;

/**
* Constructor: must have a valid open socket
**/
public WebWorker(Socket s)
{
   socket = s;
}

/**
* Worker thread starting point. Each worker handles just one HTTP 
* request and then returns, which destroys the thread. This method
* assumes that whoever created the worker created it with a valid
* open socket object.
**/
public void run()
{
   System.err.println("Handling connection...");
   try {
      boolean status = false;
      InputStream  is = socket.getInputStream();
      OutputStream os = socket.getOutputStream();
      File serverFileHandle;
      if((serverFileHandle = readHTTPRequest(is)) != null){
         status = true;
      }
      //We need to "catch" whether readHTTPRequest was passed a valid file path, if it wasn't, write an 404 response.
      //Otherwise, continue.
      writeHTTPHeader(os,"text/html", status);
      writeContent(os, serverFileHandle);
      os.flush();
      socket.close();
   } catch (Exception e) {
      System.err.println("Output error: "+e);
   }
   System.err.println("Done handling connection.");
   return;
}

/*
 Read the HTTP request header.
 Change the return type from void to int. That way we can pass whether it 
 was a regular response or an error response without using another argument.
*/
  
private File readHTTPRequest(InputStream is)
{
   //Declaring various necessary variables.
   File checkFile = null;
   String line;
   String fileLine;
   BufferedReader r = new BufferedReader(new InputStreamReader(is));
   boolean responseStatus = false;
   boolean fileFound = false;
   
   while (true) {
      try {
         while (!r.ready()) Thread.sleep(1);
         line = r.readLine();
         
         //Fetch the name of the file.
         if((line.indexOf('/') > -1) && !fileFound){
            fileLine = line.substring(line.indexOf('/')); //If A line might be a file, take a look at it.
            
            //If a file has a spece at the end of it, cut it off.
            if(fileLine.indexOf(' ') > -1) fileLine = fileLine.substring(fileLine.indexOf('/'), fileLine.indexOf(' '));

            //Make sure the file reader knows we are starting our search in the current directory.
            String filePath = "." + fileLine;
            checkFile = new File(filePath); 
            
            //Check to make sure the file exists. (Thanks to StackOverFlow for this code snippet. [Sean A.O. Harney (November 29/2009]).
            if(checkFile.exists() && !checkFile.isDirectory()){  
               fileFound = true;
               System.out.println("File Found! File is: " + filePath);
            }
            System.out.println("File yanked: " + fileLine);
         }

         System.err.println("Request line: ("+line+")");
         if (line.length()==0) break;
      } catch (Exception e) {
         System.err.println("Request error: "+e);
         break;
      }
   }
   if(!fileFound) return null;
   return checkFile;
}

/**
* Write the HTTP header lines to the client network connection.
* @param os is the OutputStream object to write to
* @param contentType is the string MIME content type (e.g. "text/html")
**/
private void writeHTTPHeader(OutputStream os, String contentType, boolean status) throws Exception
{
   //Configure the date.
   Date d = new Date();
   DateFormat df = DateFormat.getDateTimeInstance();
   df.setTimeZone(TimeZone.getTimeZone("GMT"));
   
   //Write out the response header.
   os.write(responseHeader(status).getBytes());
   
   //Tell us the content of the message.
   os.write(contentType.getBytes());

   //Delimit the header with two newline characters.
   os.write("\n\n".getBytes()); // HTTP header ends with 2 newlines
   return;
}

/**
* Write the data content to the client network connection. This MUST
* be done after the HTTP header has been written out.
* @param os is the OutputStream object to write to
**/
private void writeContent(OutputStream os, File fileHandle) throws Exception
{ 
   //Making sure the file we were passed was legit.
   if(fileHandle == null){ 
      System.err.println("The file passed to the server was null. Please be wary of client. . .");

      //Tell the end user what's up, since most of us don't read response header's for fun.
      os.write("<html><h1>404 NOT FOUND!</h1><body><p>The resource you requested is not present on the server. <br/><br/>Sorry.:( </p></body></html>".getBytes());
      return; 
   }

   //Setting up the various strings and file readears.
   BufferedReader r = new BufferedReader(new FileReader(fileHandle));
   String line = "";
   int termChar = -1; //Wasteful, I know.
   
   //Setting up the variables we use for date printing.
   Date serverDate = new Date();
   DateFormat df = DateFormat.getDateTimeInstance();
   df.setTimeZone(TimeZone.getTimeZone("GMT"));

   //Setting up the dynamic server side content generation.
   CharSequence dateTarget = "<cs371date>";
   CharSequence dateReplacement = df.format(serverDate);
   CharSequence serverTarget = "<cs371server>";
   CharSequence serverReplacement = "Mein ehefrau ist tot, das gut.";
     
   
   //Reading all the lines from the file readers.
   while(true){
      if(!r.ready()){ 
         Thread.sleep(1);
         continue;
      }
      
      line = r.readLine();
      termChar = r.read();
      
      //Replacing all instances of <cs371date> and <cs371time>
      line = line.replace(dateTarget, dateReplacement);
      line = line.replace(serverTarget, serverReplacement);     

      System.out.println("This is the line passed from the file: " + line + "\n");
      if(termChar == -1) os.write(line.getBytes());
      else{
         os.write(line.getBytes());
         os.write(termChar);
      }
      
      if(line.indexOf("</html>") > -1) return;
   }
}

//Making a response header that can handle switching between 404 and 200 depending on the status of a file.
private String responseHeader(boolean status){
   Date d = new Date();
   DateFormat df = DateFormat.getDateTimeInstance();
   df.setTimeZone(TimeZone.getTimeZone("GMT"));

   String contentBody;
   if(status) contentBody = "HTTP/1.1 200 OK\n";
   else       contentBody = "HTTP/1.1 404 NOT FOUND\n";

   contentBody += "Date: " + df.format(d) +
                  "\nServer: Zachary's crummy server\n" +
                  "Connection: close\nContent-Type: ";
   
   System.out.println(contentBody);
   return contentBody;
}


} // end class
