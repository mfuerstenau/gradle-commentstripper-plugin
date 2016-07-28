package de.fuerstenau.gradle.commentstripper;

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
import java.nio.file.Paths;
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
           CommentStripper.class.getCanonicalName ());

   static void stripComments (final Path srcDir, final Path destDir, final Charset cs, final String keyword, EnumSet<CommentType> types) throws IOException
   {
      Worker.initConditional (new SimpleConditional (types, keyword));
      final PathMatcher javaMatcher = FileSystems.getDefault ().getPathMatcher ("glob:" + "*.java");
      final ExecutorService executor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors ());
      final List<Future<Void>> results = new ArrayList<> ();

      Files.walkFileTree (srcDir, EnumSet.noneOf (FileVisitOption.class), Integer.MAX_VALUE,
              new SimpleFileVisitor<Path> ()
      {
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

            if (javaMatcher.matches (file.getFileName ()))
            {
               results.add (executor.submit (new Worker (file, destDir.resolve (srcDir.relativize (file)), cs)));
            }
            else
            {
               Files.copy (file, outFilePath);
            }
            return FileVisitResult.CONTINUE;
         }
      });

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

   CommentStripper ()
   {
      this.cond = new DefaultConditional ();
   }

   public static void main (String[] args) throws IOException
   {
//      CommentStripper stripper = new CommentStripper (new SimpleConditional (EnumSet.of (CommentType.LINE), "---"));
//      stripper.doStrip (Paths.get ("C:\\Users\\Nuffe\\Documents\\NetBeansProjects\\gradle-commentstripper-plugin\\xxx\\HelloBuildConfig.java"), Paths.get ("C:\\Users\\Nuffe\\Documents\\NetBeansProjects\\gradle-commentstripper-plugin\\xxx\\HelloBuildConfig_out.java"), Charsets.UTF_8);
   }
   
   private void doStrip (Path fin, Path fout, Charset cs) throws IOException
   {
      try (Reader in = Files.newBufferedReader (fin, cs);
              OutputStream out = new BufferedOutputStream (Files.newOutputStream (fout)))
      {
         final ANTLRInputStream stream = new ANTLRInputStream (in);

         CommonTokenStream tokens = new CommonTokenStream (new Java8Lexer (stream), Java8Lexer.COMMENTS);

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

   static class DefaultConditional extends FirstStringMatchConditional
   {

      DefaultConditional ()
      {
         super ("\\.");
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
               return true;
            
            return containsSequence (comment, commentType);
         }
         
         return false;
      }
      
   }
   
   abstract static class FirstStringMatchConditional implements CommentStrippingConditional
   {

      private Pattern p;
      private Matcher m;
      private final String s;

      FirstStringMatchConditional (String s)
      {
         this.s = s;
      }

      private Pattern pattern ()
      {
         if (p == null)
         {
            p = Pattern.compile ("^\\/(?:(?:\\/\\s{0,127}+)|(?:[\\*\\s]{0,127}+))" + s);
         }
         return p;
      }

      private Matcher matcher (CharSequence s)
      {
         if (m == null)
         {
            m = pattern ().matcher (s);
         }
         else
         {
            m.reset (s);
         }
         return m;
      }

      @Override
      public boolean shouldStrip (String comment)
      {
         return matcher (comment).find ();
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
               conditional = new DefaultConditional ();
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

      Worker (Path inFile, Path outFile, Charset charset)
      {
         this.inFile = inFile;
         this.outFile = outFile;
         this.charset = charset;
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
         STRIPPER_THREAD_LOCAL.get ().doStrip (inFile, outFile, charset);
         return null;
      }

   }

   interface CommentStrippingConditional
   {

      boolean shouldStrip (String comment);
   }
}
