/*
    Copyright 2011 Robert Siemer

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package sh.backsla.mustache;

import com.ichi2.anki.Utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Template {
    private static final String START_DEFAULT = Pattern.quote("{{");
    private static final String END_DEFAULT = Pattern.quote("}}");
    private static final String TYPE_SECTION = "#^/";
    private static final String TYPE_ALL = "{>&=!" + TYPE_SECTION;
    // my Mustache pattern is: (may not contain newline at all (standard would allow for them in comments))
    // start delimiter, tag type, space+tabs,
    // key (everything except both delimiters), space+tabs, tag type end, end delimiter
    // tag type start and end must match (but Java and Android’s ICU regex don’t have conditionals)
    private static final String PREFORMAT =
            "%1$s   ([ %3$s ]?)   \\p{Blank}*   ((?: (?! %1$s ) (?! %2$s ) . )+?)   \\p{Blank}*   ([=}]?)   %2$s";
    private static final int GROUP_ALL = 0;
    private static final int GROUP_TYPE = 1;
    private static final int GROUP_KEY = 2;
    private static final int GROUP_TYPE_END = 3;
    private static final String NORMAL_STRING = String.format(PREFORMAT, START_DEFAULT, END_DEFAULT, TYPE_ALL);
    private static final String SECTION_STRING = String.format(PREFORMAT, START_DEFAULT, END_DEFAULT, TYPE_SECTION);
    private static final Pattern NORMAL_PATTERN = Pattern.compile(NORMAL_STRING, Pattern.COMMENTS);
    private static final Pattern SECTION_PATTERN = Pattern.compile(SECTION_STRING, Pattern.COMMENTS);
    private final String mTemplate;
    private Pattern mNormalPattern;
    private Pattern mSectionPattern;


    /**
     * Instantiates a new Mustache template.
     *
     * @param template the Mustache String in the Panki-Mustache variant
     */
    public Template(String template) {
        mTemplate = template;
        mNormalPattern = NORMAL_PATTERN;
        mSectionPattern = SECTION_PATTERN;
    }


    /**
     * Applies the Map context on this Mustache template. Errors like missing variables/keys in the context, wrong
     * syntax or non-matching section names are ignored by reproducing the affecting tag literally. Don’t rely on this
     * virgin look of the “error messages”, because they look like the tag got ignored, which is not the intention and
     * may change.
     *
     * @param context the Map holding <i>all</i> referenced String variables and their value.
     * @return the result of formating this template with the context
     */
    public String execute(Map<String, String> context) {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = mNormalPattern.matcher(mTemplate);
        while (matcher.find()) {
            String literal = matcher.group(GROUP_ALL);
            String tagType = matcher.group(GROUP_TYPE);
            String key = matcher.group(GROUP_KEY);
            String tagTypeEnd = matcher.group(GROUP_TYPE_END);
            if (tagTypeEnd.equals("")) {
                if (tagType.equals("")) {
                    // standard says: should render nothing if key does not exist, I render literal
                    // standard also demands: escape HTML,
                    // here: span, no escape
                    appendLiteral(matcher, sb, context.containsKey(key) ? wrapSpan(context, key) : literal);
                } else if (tagType.equals(">")) {
                    // “partial”: run recursive: use current context and value as Template
                    appendLiteral(matcher, sb,
                            context.containsKey(key) ? new Template(context.get(key)).execute(context) : literal);
                } else if (tagType.equals("#") || tagType.equals("^")) {
                    handleSection(matcher, sb, context, literal, tagType, key);
                } else if (tagType.equals("&")) {
                    // no span, no escaping: raw
                    appendLiteral(matcher, sb, context.containsKey(key) ? context.get(key) : literal);
                } else if (tagType.equals("!")) {
                    // comment
                    matcher.appendReplacement(sb, "");
                } else if (tagType.equals("/")) {
                    // closing tag without opening
                    appendLiteral(matcher, sb, literal);
                } else {
                    // e.g. = or {, with closing tag
                    appendLiteral(matcher, sb, literal);
                }
            } else if (tagTypeEnd.equals("}")) {
                if (tagType.equals("{")) {
                    // no span, html-escaping
                    appendLiteral(matcher, sb,
                            context.containsKey(key) ? Utils.escapeHtml(context.get(key), false) : literal);
                } else {
                    // {{? }}}
                    appendLiteral(matcher, sb, literal);
                }
            } else { // tagTypeEnd.equals("=")
                // error or possibly delimiter switch, which is not supported
                appendLiteral(matcher, sb, literal);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }


    /**
     * Wrap a HTML span element with class “key” attribute around the corresponding “value” in the context. This enables
     * easy CSS reference to this “field”. The class name corresponds to the literal key value, accordingly escaped.
     * Apart from space characters (HTML limitation), practically everything can form the class name. But even space
     * characters are okay for this function, but will be mapped to underscore.
     *
     * @param context the Map to lookup the HTML text in.
     * @param key the key to retrieve the HTML text and to name the class.
     * @return the string
     */
    public static String wrapSpan(Map<String, String> context, String key) {
        return String.format("<span class='%s'>%s</span>", Utils.escapeHtml(key, true), context.get(key));
    }


    private static void appendLiteral(Matcher matcher, StringBuffer sb, String literal) {
        matcher.appendReplacement(sb, "");
        sb.append(literal);
    }


    private void handleSection(Matcher matcher, StringBuffer sb, Map<String, String> context,
            String literal, String tagType, String key) {
        // save the input up to the beginning of the section
        matcher.appendReplacement(sb, "");
        matcher.usePattern(mSectionPattern);
        StringBuffer section = new StringBuffer();
        boolean eof = collectSection(matcher, section);
        if (eof || matcher.group(GROUP_KEY).equals(key)) {
            if (context.containsKey(key)) {
                // section valid
                boolean keyEmpty = context.get(key).equals("");
                if ((keyEmpty && tagType.equals("^")) || (!keyEmpty && tagType.equals("#"))) {
                    // evaluate the section once
                    sb.append(new Template(section.toString()).execute(context));
                } else {
                    // drop the section
                }
            } else {
                // key not there: render literal
                sb.append(literal + section + (eof ? "" : matcher.group(GROUP_ALL)));
            }
        } else {
            // section start/end keys don’t match: also render literal
            sb.append(literal + section + matcher.group(GROUP_ALL));
        }
        matcher.usePattern(mNormalPattern);
    }

    /**
     * Collects the current section without start or end tag.
     *
     * @param matcher is supposed to be just behind the start tag and is used up to the end tag or until find() fails.
     * @param section is where the whole section as-is is appended to.
     * @return true if the we hit end of input and the matcher is in an “unmatched” state.
     */
    private static boolean collectSection(Matcher matcher, StringBuffer section) {
        // collect the section in &section until EOF or our closing tag is found
        int depth = 1;
        while (depth > 0 && matcher.find()) {
            String lLiteral = matcher.group(GROUP_ALL);
            String lTagType = matcher.group(GROUP_TYPE);
            String lTagTypeEnd = matcher.group(GROUP_TYPE_END);
            if (lTagTypeEnd.equals("")) {
                // inner sections may have non-matching names from this point of view
                depth += lTagType.equals("/") ? -1 : 1;
            }
            // I “report” errors with the tag literal, which is also what I need if there is no error...
            appendLiteral(matcher, section, depth == 0 ? "" : lLiteral);
        }
        return depth > 0;
    }
}

