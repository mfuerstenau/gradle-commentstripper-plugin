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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.PluginManager
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.compile.JavaCompile
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Malte Fürstenau
 */
class CommentStripperPlugin implements Plugin<Project>
{

    static final String MAIN_SOURCESET = "main"
    static final String FD_SOURCE_OUTPUT = "gen/commentstripper/src"

    private static final Logger LOG = LoggerFactory.getLogger (
        CommentStripperPlugin.class.getCanonicalName ())

    private Project p
   
    @Override
    void apply (Project p)
    {
        this.p = p
        p.apply plugin: 'java'

        LOG.debug "CommentStripperPlugin loaded."
    }
}
