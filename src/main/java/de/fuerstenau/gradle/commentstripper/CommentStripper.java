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
package de.fuerstenau.gradle.commentstripper;

import de.fuerstenau.gradle.commentstripper.antlr.GroovyLexer;
import de.fuerstenau.gradle.commentstripper.antlr.Java8Lexer;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import static org.antlr.v4.runtime.Token.EOF;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CommentStripper
{

   private static final Logger LOG = LoggerFactory.getLogger (
           CommentStripper.class);

   static void stripComments (final Path srcDir, final Path destDir, final Charset cs, final String keyword, EnumSet<CommentType> types, final DocType docType) throws IOException
   {

      String glob = "glob:";
      if (null != docType)
      {
         switch (docType)
         {
            case GROOVY:
            {
               glob = glob + "*.groovy";
               break;
            }
            case JAVA:
            {
               glob = glob + "*.java";
               break;
            }
            default:
            {
               throw new IllegalArgumentException ("docType MUST not be null");
            }
         }
      }
      else
      {
         throw new IllegalArgumentException ("docType MUST not be null");
      }

      Worker.initConditional (new SimpleConditional (types, keyword));

      final PathMatcher fileMatcher = FileSystems.getDefault ().getPathMatcher (glob);
      final ExecutorService executor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors ());
      final List<Future<Void>> results = new ArrayList<> ();

      Files.walkFileTree (srcDir, EnumSet.noneOf (FileVisitOption.class), Integer.MAX_VALUE,
              new StrippingFileVisitor (destDir, srcDir, fileMatcher, results, executor, cs, docType));

      executor.shutdown ();

      boolean error = false;

      try
      {
         for (Future<Void> result : results)
         {
            try
            {
               result.get ();
            }
            catch (InterruptedException | ExecutionException ex)
            {
               error = true;
               throw new RuntimeException (ex);
            }
         }

      }
      finally
      {
         Worker.removeThreadLocal ();
         if (error)
         {
            LOG.debug ("Canceling worker threads.");
            for (Future<Void> result : results)
            {
               result.cancel (true);
            }
         }
         try
         {
            executor.awaitTermination (2, TimeUnit.SECONDS);
            LOG.debug ("Threadpool successfully shut down.");
         }
         catch (InterruptedException ex)
         {
            LOG.debug ("Interrupted.");
         }
      }
   }

   private final CommentStrippingConditional cond;

   CommentStripper (CommentStrippingConditional cond)
   {
      this.cond = cond;
   }

   public static void main (String[] args) throws IOException
   {
//      CommentStripper stripper = new CommentStripper (new SimpleConditional (EnumSet.of (CommentType.LINE), "---"));
//      stripper.doStrip (Paths.get ("C:\\Users\\Nuffe\\Documents\\NetBeansProjects\\gradle-commentstripper-plugin\\xxx\\HelloBuildConfig.java"), Paths.get ("C:\\Users\\Nuffe\\Documents\\NetBeansProjects\\gradle-commentstripper-plugin\\xxx\\HelloBuildConfig_out.java"), Charsets.UTF_8);
   }

   private void doStrip (Path fin, Path fout, Charset cs, DocType docType) throws IOException
   {
      try (Reader in = Files.newBufferedReader (fin, cs);
              OutputStream out = new BufferedOutputStream (Files.newOutputStream (fout)))
      {
         final ANTLRInputStream stream = new ANTLRInputStream (in);

         final CommonTokenStream tokens;
         if (docType == DocType.JAVA)
         {
            tokens = new CommonTokenStream (new GroovyLexer (stream), GroovyLexer.COMMENT);
         }
         else
         {
            tokens = new CommonTokenStream (new Java8Lexer (stream), Java8Lexer.COMMENTS);
         }

         TokenStreamRewriter rew = new TokenStreamRewriter (tokens);

         while (tokens.LA (1) != EOF)
         {
            final Token t = tokens.LT (1);
            if (cond.shouldStrip (t.getText ()))
            {
               rew.delete (t);
            }
            tokens.consume ();
         }

         out.write (rew.getText ().getBytes (cs));
         out.flush ();
      }
   }

   public static final class SimpleConditional implements CommentStrippingConditional
   {

      private final EnumSet<CommentType> commentSequenceMatchers;

      private final String sequence;

      private final boolean sequenceEnabled;
      private Matcher commentStartMatcher;
      private final EnumMap<CommentType, Matcher> sequenceMatcher = new EnumMap<> (CommentType.class);

      public SimpleConditional (EnumSet<CommentType> commentTypes, String sequence)
      {
         this.commentSequenceMatchers = commentTypes;
         this.sequence = sequence;
         sequenceEnabled = sequence != null && !sequence.isEmpty ();
      }

      private boolean containsSequence (String text, CommentType commentType)
      {
         boolean res = false;
         if (commentType != null && text != null && !text.isEmpty ())
         {
            Matcher mx = sequenceMatcher.get (commentType);
            switch (commentType)
            {
               default:
               case BLOCK:
               case DOC:
               {
                  if (mx == null)
                  {
                     mx = Pattern.compile ("^(?:\\s++\\*?+)*+" + Pattern.quote (sequence)).matcher (text.substring (3));
                     sequenceMatcher.put (commentType, mx);
                  }
                  else
                  {
                     mx.reset (text.substring (3));
                  }
                  break;
               }
               case LINE:
               {
                  if (mx == null)
                  {
                     mx = Pattern.compile ("^\\s*+" + Pattern.quote (sequence)).matcher (text.substring (2));
                     sequenceMatcher.put (commentType, mx);
                  }
                  else
                  {
                     mx.reset (text.substring (3));
                  }
                  break;
               }
            }
            res = mx.find ();
         }
         return res;
      }

      private CommentType commentType (String s)
      {
         CommentType res = null;
         if (commentStartMatcher == null)
         {
            commentStartMatcher = Pattern.compile ("^\\/(?:\\/|\\*(?:\\*(?!\\/))?)").matcher (s);
         }
         else
         {
            commentStartMatcher.reset (s);
         }
         if (commentStartMatcher.find ())
         {

            switch (commentStartMatcher.group ())
            {
               case "//":
               {
                  res = CommentType.LINE;
                  break;
               }
               case "/*":
               {
                  res = CommentType.BLOCK;
                  break;
               }
               case "/**":
               {
                  res = CommentType.DOC;
                  break;
               }
            }
         }
         return res;
      }

      @Override
      public boolean shouldStrip (String comment)
      {
         CommentType commentType = commentType (comment);
         if (commentType != null && commentSequenceMatchers.contains (commentType))
         {
            if (!sequenceEnabled)
            {
               return true;
            }

            return containsSequence (comment, commentType);
         }

         return false;
      }

   }

   private static class Worker implements Callable<Void>
   {

      private static CommentStrippingConditional conditional;

      private static final Object LOCK = new Object ();

      public static void initConditional (CommentStrippingConditional conditional)
      {
         synchronized (LOCK)
         {
            if (Worker.conditional == null)
            {
               Worker.conditional = conditional;
            }
         }
      }

      public static CommentStrippingConditional getInitConditional ()
      {
         synchronized (LOCK)
         {
            if (conditional == null)
            {
               conditional = new SimpleConditional (EnumSet.allOf (CommentType.class), "");
            }
            return conditional;
         }
      }

      static final ThreadLocal<CommentStripper> STRIPPER_THREAD_LOCAL = new ThreadLocal<CommentStripper> ()
      {
         @Override
         public CommentStripper initialValue ()
         {
            return new CommentStripper (getInitConditional ());
         }
      };

      private final Path inFile;
      private final Path outFile;
      private final Charset charset;
      private final DocType docType;

      Worker (Path inFile, Path outFile, Charset charset, DocType docType)
      {
         this.inFile = inFile;
         this.outFile = outFile;
         this.charset = charset;
         this.docType = docType;
      }

      static void removeThreadLocal ()
      {
         if (STRIPPER_THREAD_LOCAL != null)
         {
            STRIPPER_THREAD_LOCAL.remove ();
         }
      }

      @Override
      public Void call () throws Exception
      {
         STRIPPER_THREAD_LOCAL.get ().doStrip (inFile, outFile, charset, docType);
         return null;
      }

   }

   interface CommentStrippingConditional
   {

      boolean shouldStrip (String comment);
   }

   private static class StrippingFileVisitor extends SimpleFileVisitor<Path>
   {

      private final Path destDir;
      private final Path srcDir;
      private final PathMatcher fileMatcher;
      private final List<Future<Void>> results;
      private final ExecutorService executor;
      private final Charset cs;
      private final DocType docType;

      public StrippingFileVisitor (Path destDir, Path srcDir, PathMatcher fileMatcher, List<Future<Void>> results, ExecutorService executor, Charset cs, DocType docType)
      {
         this.destDir = destDir;
         this.srcDir = srcDir;
         this.fileMatcher = fileMatcher;
         this.results = results;
         this.executor = executor;
         this.cs = cs;
         this.docType = docType;
      }

      @Override
      public FileVisitResult preVisitDirectory (Path dir, BasicFileAttributes attrs) throws IOException
      {
         Path target = destDir.resolve (srcDir.relativize (dir));
         try
         {
            Files.copy (dir, target);
         }
         catch (FileAlreadyExistsException e)
         {
            if (!Files.isDirectory (target))
            {
               throw e;
            }
         }
         return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) throws IOException
      {
         Path outFilePath = destDir.resolve (srcDir.relativize (file));
         if (Files.exists (outFilePath))
         {
            throw new FileAlreadyExistsException (outFilePath.toString ());
         }

         if (fileMatcher.matches (file.getFileName ()))
         {
            results.add (executor.submit (new Worker (file, destDir.resolve (srcDir.relativize (file)), cs, docType)));
         }
         else
         {
            Files.copy (file, outFilePath);
         }
         return FileVisitResult.CONTINUE;
      }
   }
}
