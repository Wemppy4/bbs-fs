package mchorse.bbs_mod.fonts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/** Shared codepoint/formatting layout for native drawing, measuring and wrapping. */
public final class FontTextLayout
{
    public static final class Style
    {
        public char color;
        public boolean bold, italic, underline, strike, random;
        Style copy()
        {
            Style s = new Style();
            s.color = this.color; s.bold = this.bold; s.italic = this.italic;
            s.underline = this.underline; s.strike = this.strike; s.random = this.random;
            return s;
        }
        void format(char code)
        {
            code = Character.toLowerCase(code);
            if ("0123456789abcdef".indexOf(code) >= 0 || code == 'r')
            {
                this.color = code == 'r' ? 0 : code;
                this.bold = this.italic = this.underline = this.strike = this.random = false;
            }
            else if (code == 'k') this.random = true;
            else if (code == 'l') this.bold = true;
            else if (code == 'm') this.strike = true;
            else if (code == 'n') this.underline = true;
            else if (code == 'o') this.italic = true;
        }
        String prefix()
        {
            StringBuilder b = new StringBuilder();
            if (this.color != 0) b.append('\u00a7').append(this.color);
            if (this.random) b.append("\u00a7k");
            if (this.bold) b.append("\u00a7l");
            if (this.strike) b.append("\u00a7m");
            if (this.underline) b.append("\u00a7n");
            if (this.italic) b.append("\u00a7o");
            return b.toString();
        }
    }
    public static final class Token
    {
        public final int start, end, codepoint;
        public final float advance;
        public final Style style;
        Token(int start, int end, int cp, float advance, Style style)
        {
            this.start = start; this.end = end; this.codepoint = cp;
            this.advance = advance; this.style = style;
        }
    }
    public static List<Token> tokens(String text, IntToDoubleFunction advances)
    {
        return tokens(text, advances, cp -> 1);
    }
    public static List<Token> tokens(String text, IntToDoubleFunction advances, IntToDoubleFunction boldOffsets)
    {
        List<Token> result = new ArrayList<>();
        Style style = new Style();
        for (int i = 0; i < text.length();)
        {
            int cp = text.codePointAt(i), end = i + Character.charCount(cp);
            if (cp == 167 && end < text.length())
            {
                style.format(text.charAt(end));
                i = end + 1;
                continue;
            }
            float advance = cp == '\n' || cp == '\r' ? 0 : (float) advances.applyAsDouble(cp) + (style.bold ? (float) boldOffsets.applyAsDouble(cp) : 0);
            result.add(new Token(i, end, cp, advance, style.copy()));
            i = end;
        }
        return result;
    }
    public static int width(String text, IntToDoubleFunction advances)
    {
        return width(text, advances, cp -> 1);
    }
    public static int width(String text, IntToDoubleFunction advances, IntToDoubleFunction boldOffsets)
    {
        if (text == null) return 0;
        float line = 0, max = 0;
        for (Token token : tokens(text, advances, boldOffsets))
        {
            if (token.codepoint == '\n') { max = Math.max(line, max); line = 0; }
            else line += token.advance;
        }
        return (int) Math.ceil(Math.max(line, max));
    }
    public static List<String> wrap(String text, int width, IntToDoubleFunction advances)
    {
        return wrap(text, width, advances, cp -> 1);
    }
    public static List<String> wrap(String text, int width, IntToDoubleFunction advances, IntToDoubleFunction boldOffsets)
    {
        if (text == null || text.isEmpty()) return Collections.singletonList("");
        List<Token> tokens = tokens(text, advances, boldOffsets);
        List<String> lines = new ArrayList<>();
        int rawStart = 0, lineStart = 0, i = 0, space = -1;
        float used = 0;
        String prefix = "";
        while (i < tokens.size())
        {
            Token t = tokens.get(i);
            if (t.codepoint == '\n')
            {
                lines.add(prefix + text.substring(rawStart, t.start));
                rawStart = t.end; lineStart = ++i; space = -1; used = 0;
                prefix = t.style.prefix();
                continue;
            }
            if (t.codepoint == ' ') space = i;
            if (used + t.advance > Math.max(0, width) && i > lineStart)
            {
                int next;
                if (space >= lineStart)
                {
                    Token split = tokens.get(space);
                    lines.add(prefix + text.substring(rawStart, split.start));
                    rawStart = split.end; next = space + 1;
                    prefix = split.style.prefix();
                }
                else
                {
                    lines.add(prefix + text.substring(rawStart, t.start));
                    rawStart = t.start; next = i; prefix = t.style.prefix();
                }
                i = lineStart = next; space = -1; used = 0;
                continue;
            }
            used += t.advance;
            i++;
        }
        lines.add(prefix + text.substring(rawStart));
        return lines;
    }
    public static String trim(String text, int width, boolean reverse, IntToDoubleFunction advances)
    {
        return trim(text, width, reverse, advances, cp -> 1);
    }
    public static String trim(String text, int width, boolean reverse, IntToDoubleFunction advances, IntToDoubleFunction boldOffsets)
    {
        if (text == null || text.isEmpty()) return "";
        List<Token> tokens = tokens(text, advances, boldOffsets);
        float used = 0;
        if (reverse)
        {
            int start = text.length();
            Style style = null;
            for (int i = tokens.size() - 1; i >= 0; i--)
            {
                Token t = tokens.get(i);
                if (t.codepoint == '\n' || used + t.advance > width) break;
                start = t.start; style = t.style; used += t.advance;
            }
            return style == null ? "" : style.prefix() + text.substring(start);
        }
        int end = 0;
        for (Token t : tokens)
        {
            if (t.codepoint == '\n' || used + t.advance > width) break;
            end = t.end; used += t.advance;
        }
        return text.substring(0, end);
    }
}
