package us.codecraft.xsoup;

import org.jsoup.helper.Validate;
import org.jsoup.parser.TokenQueue;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author code4crafter@gmail.com
 */
public class XPathParser {

    private String[] combinators = new String[]{"//", "/", "|"};

    private XTokenQueue tq;
    private String query;
    private List<Evaluator> evals = new ArrayList<Evaluator>();
    private String attribute;

    public XPathParser(String xpathStr) {
        this.query = xpathStr;
        this.tq = new XTokenQueue(xpathStr);
    }

    public XPathEvaluator parse() {

        while (!tq.isEmpty()) {

            if (tq.matchesAny(combinators)) {
                combinator(tq.consumeAny(combinators));
            } else {
                findElements();
            }
        }

        if (evals.size() == 1)
            return new XPathEvaluator(evals.get(0), attribute);

        return new XPathEvaluator(new CombiningEvaluator.And(evals), attribute);
    }

    private void combinator(String combinator) {
        Evaluator currentEval;
        if (evals.size() == 0) {
            currentEval = new StructuralEvaluator.Root();
        } else if (evals.size() == 1) {
            currentEval = evals.get(0);
        } else {
            currentEval = new CombiningEvaluator.And(evals);
        }
        evals.clear();
        String subQuery = consumeSubQuery();
        XPathEvaluator newEval = parse(subQuery);
        if (newEval.getAttribute() != null) {
            attribute = newEval.getAttribute();
        }
        if (combinator.equals("//")) {
            currentEval = new CombiningEvaluator.And(newEval.getEvaluator(), new StructuralEvaluator.Parent(currentEval));
        } else if (combinator.equals("/")) {
            currentEval = new CombiningEvaluator.And(newEval.getEvaluator(), new StructuralEvaluator.ImmediateParent(currentEval));
        } else if (combinator.equals("|")) {
            currentEval = new CombiningEvaluator.Or(newEval.getEvaluator(), new StructuralEvaluator.ImmediateParent(currentEval));
        }
        evals.add(currentEval);

    }

    private String consumeSubQuery() {
        StringBuilder sq = new StringBuilder();
        while (!tq.isEmpty()) {
            if (tq.matches("("))
                sq.append("(").append(tq.chompBalanced('(', ')')).append(")");
            else if (tq.matches("["))
                sq.append("[").append(tq.chompBalanced('[', ']')).append("]");
            else if (tq.matchesAny(combinators))
                break;
            else
                sq.append(tq.consume());
        }
        return sq.toString();
    }

    private void findElements() {
        if (tq.matchesWord()) {
            byTag();
        } else if (tq.matchChomp("[@")) {
            byAttribute();
        } else {
            // unhandled
            throw new Selector.SelectorParseException("Could not parse query '%s': unexpected token at '%s'", query, tq.remainder());
        }

    }

    private void byId() {
        String id = tq.consumeCssIdentifier();
        Validate.notEmpty(id);
        evals.add(new Evaluator.Id(id));
    }

    private void byClass() {
        String className = tq.consumeCssIdentifier();
        Validate.notEmpty(className);
        evals.add(new Evaluator.Class(className.trim().toLowerCase()));
    }

    private void byTag() {
        String tagName = tq.consumeElementSelector();
        Validate.notEmpty(tagName);

        // namespaces: if element name is "abc:def", selector must be "abc|def", so flip:
        if (tagName.contains("|"))
            tagName = tagName.replace("|", ":");

        evals.add(new Evaluator.Tag(tagName.trim().toLowerCase()));
    }

    private void byAttribute() {
        TokenQueue cq = new TokenQueue(tq.chompBalanced('[', ']')); // content queue
        String key = cq.consumeToAny("=", "!=", "^=", "$=", "*=", "~="); // eq, not, start, end, contain, match, (no val)
        Validate.notEmpty(key);
        cq.consumeWhitespace();

        if (cq.isEmpty()) {
            if (key.startsWith("^"))
                evals.add(new Evaluator.AttributeStarting(key.substring(1)));
            else
                evals.add(new Evaluator.Attribute(key));
        } else {
            if (cq.matchChomp("="))
                evals.add(new Evaluator.AttributeWithValue(key, cq.remainder()));

            else if (cq.matchChomp("!="))
                evals.add(new Evaluator.AttributeWithValueNot(key, cq.remainder()));

            else if (cq.matchChomp("^="))
                evals.add(new Evaluator.AttributeWithValueStarting(key, cq.remainder()));

            else if (cq.matchChomp("$="))
                evals.add(new Evaluator.AttributeWithValueEnding(key, cq.remainder()));

            else if (cq.matchChomp("*="))
                evals.add(new Evaluator.AttributeWithValueContaining(key, cq.remainder()));

            else if (cq.matchChomp("~="))
                evals.add(new Evaluator.AttributeWithValueMatching(key, Pattern.compile(cq.remainder())));
            else
                throw new Selector.SelectorParseException("Could not parse attribute query '%s': unexpected token at '%s'", query, cq.remainder());
        }
    }

    public static XPathEvaluator parse(String xpathStr) {
        XPathParser xPathParser = new XPathParser(xpathStr);
        return xPathParser.parse();
    }

}
