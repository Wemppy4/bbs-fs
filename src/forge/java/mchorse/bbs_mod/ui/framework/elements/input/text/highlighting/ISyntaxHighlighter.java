package mchorse.bbs_mod.ui.framework.elements.input.text.highlighting;

import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;

import java.util.List;

public interface ISyntaxHighlighter
{
    public SyntaxStyle getStyle();

    public void setStyle(SyntaxStyle style);

    public List<TextSegment> parse(FontRenderer font, List<HighlightedTextLine> textLines, String line, int lineIndex);
}