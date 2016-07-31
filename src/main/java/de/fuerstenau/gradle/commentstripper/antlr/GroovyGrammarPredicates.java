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
package de.fuerstenau.gradle.commentstripper.antlr;

import static de.fuerstenau.gradle.commentstripper.antlr.GroovyLexer.VISIBILITY_MODIFIER;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.TokenStream;

/**
 *
 * @author Nuffe
 */
public class GroovyGrammarPredicates
{

   public static boolean isCurrentClassName (TokenStream tokenStream, String currentClassName)
   {
      return tokenStream.LT (tokenStream.LT (1).getType () == VISIBILITY_MODIFIER ? 2 : 1).getText ().equals (currentClassName);
   }

   public static boolean isFollowedByJavaLetterInGString (CharStream cs)
   {
      int c1 = cs.LA (1);
      String str1 = String.valueOf ((char) c1);

      if (str1.matches ("[a-zA-Z_{]"))
      {
         return true;
      }

      if (str1.matches ("[^\u0000-\u007F\uD800-\uDBFF]")
              && Character.isJavaIdentifierPart (c1))
      {
         return true;
      }

      int c2 = cs.LA (2);
      String str2 = String.valueOf ((char) c2);

      if (str1.matches ("[\uD800-\uDBFF]")
              && str2.matches ("[\uDC00-\uDFFF]")
              && Character.isJavaIdentifierPart (Character.toCodePoint ((char) c1, (char) c2)))
      {

         return true;
      }

      return false;
   }
}
