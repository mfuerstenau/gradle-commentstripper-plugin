/* The MIT License (MIT)
 *
 * Copyright (c) 2015 Malte Fürstenau
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package de.fuerstenau.gradle.commentstripper

import java.nio.charset.Charset
import java.util.zip.CRC32
import java.nio.file.Path
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import groovy.transform.CompileStatic
/**
 * @author Malte Fürstenau
 */
@CompileStatic
abstract class BaseCommentStripperTask extends DefaultTask
{

   private static final Logger LOG = LoggerFactory.getLogger (
      BaseCommentStripperTask)

   /**
    * Deletes a directory and recreates it.
    *
    * @param outputDir Directory
    * @throws IOException thrown if I/O error occurs
    */
   private static void emptyDir (final File outputDir) throws IOException
   {
      outputDir.toPath ().deleteDir ()
      outputDir.mkdirs ()
   }

   @InputFiles
   Set<File> inputFiles

   @Input
   String charset

   @Input
   boolean stripDoc

   @Input
   boolean stripLine
    
   @Input
   boolean stripBlock
    
   @Input
   String mark

   @OutputDirectories
   Set<File> outputDirectories
   
   protected DocType docType;

   BaseCommentStripperTask ()
   {
      charset = "UTF-8"
      mark = ''
      stripBlock = true;
      stripLine = true;
      stripDoc= true;
      LOG.debug "{}: CommentStripperTask created, mark={} ", name, mark
   }

   private Path getOutPDir (Path inDir)
   {
      return project.buildDir.toPath ().resolve(CommentStripperPlugin.FD_SOURCE_OUTPUT).resolve (getCRC32 (inDir))
   }
    

   protected Set<File> getOutputDirectories ()
   {
      Set<File> outDirs = new HashSet<> ()
      inputFiles.each { File idir ->
         outDirs.add(getOutPDir (idir.toPath ()).toFile ())
      }
      return outDirs
   }

   private static String getCRC32 (Path inDir)
   {
      CRC32 checksum = new CRC32();
      byte[] bytes = inDir.toAbsolutePath ().toString ().getBytes ()
      checksum.update(bytes, 0, bytes.length)
      Long.toHexString (checksum.getValue ());
   }

   @TaskAction
   void stripComments () throws IOException
   {
      LOG.debug "{}: CommentStripperTask executed.", name
      emptyDir (project.buildDir.toPath ().resolve (CommentStripperPlugin.FD_SOURCE_OUTPUT).toFile ())
        
      inputFiles.each { File dir ->
         Path outputDir = getOutPDir (dir.toPath ())
         outputDir.toFile ().mkdirs ()
            
         def types = EnumSet.noneOf (CommentType.class)
         if (stripDoc)
         types.add (CommentType.DOC)
         if (stripBlock)
         types.add (CommentType.BLOCK)
         if (stripLine)
         types.add (CommentType.LINE)
         LOG.debug "{}: Stripping source files from <{}> to <{}> using keyword <{}> for {}.", name, dir, outputDir, mark, types
         CommentStripper.stripComments (dir.toPath (), getOutPDir (dir.toPath ()), Charset.forName (charset), mark, types, docType)
      }
   }
}
