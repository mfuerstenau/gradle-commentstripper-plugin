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

import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.UnknownPluginException
import groovy.transform.CompileStatic
import org.gradle.api.GradleScriptException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class GroovyCommentStripperTask extends BaseCommentStripperTask
{
   private static final Logger LOG = LoggerFactory.getLogger (GroovyCommentStripperTask)

   @groovy.transform.TypeChecked(groovy.transform.TypeCheckingMode.SKIP)
   private Set<File> getMainGroovySourceSet ()
   {
      ((org.gradle.api.file.SourceDirectorySet) project.convention.getPlugin (JavaPluginConvention).getSourceSets ().getByName ('main').getProperty ('groovy')).srcDirs
   }
   
   GroovyCommentStripperTask ()
   {
      super ()
      try
      {
         project.getPlugins ().getPlugin (GroovyPlugin)
      }
      catch (UnknownPluginException ex)
      {
         throw new GradleScriptException ('GroovyCommentStripperTask needs Gradle GroovyPlugin to be applied.', ex)
      }
      
      inputFiles = getMainGroovySourceSet ()
      docType = DocType.GROOVY;
      outputDirectories = getOutputDirectories ()
   }
}
